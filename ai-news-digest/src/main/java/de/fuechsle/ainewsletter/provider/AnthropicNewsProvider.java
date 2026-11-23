package de.fuechsle.ainewsletter.provider;

import com.anthropic.models.messages.ServerToolUsage;
import com.anthropic.models.messages.Usage;
import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.secret.SecretProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicWebSearchTool;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * Claude via Spring AI (ADR-007). The model, its limits and the API key are resolved per call from
 * the database row of the model, so no model name is configured anywhere.
 *
 * <p>Research calls use Anthropic's server-side web search; the number of searches is reported by
 * the API and billed separately.
 */
@Component
public class AnthropicNewsProvider implements NewsProvider {

  public static final String CODE = "ANTHROPIC";

  private final SecretProvider secrets;
  private final String baseUrl;
  private final Clock clock;

  public AnthropicNewsProvider(
      SecretProvider secrets, NewsDigestProperties properties, Clock clock) {
    this.secrets = secrets;
    this.baseUrl = properties.providers().anthropic().baseUrl();
    this.clock = clock;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public ModelResponse call(ModelInvocation invocation) {
    String apiKey = secrets.getSecret(invocation.secretName());
    AnthropicChatOptions options = options(invocation, apiKey);
    AnthropicChatModel model = AnthropicChatModel.builder().options(options).build();

    Instant start = clock.instant();
    ChatResponse response;
    try {
      response = model.call(new Prompt(invocation.prompt(), options));
    } catch (RuntimeException e) {
      // The SDK messages contain status and error type, never the API key.
      throw new ProviderException(
          "Anthropic call for model " + invocation.modelName() + " failed: " + e.getMessage(), e);
    }
    Duration duration = Duration.between(start, clock.instant());
    return toModelResponse(response, duration);
  }

  AnthropicChatOptions options(ModelInvocation invocation, String apiKey) {
    AnthropicChatOptions.Builder builder =
        AnthropicChatOptions.builder()
            .model(invocation.modelName())
            .maxTokens(invocation.maxOutputTokens())
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .timeout(invocation.timeout());
    if (invocation.temperature() != null) {
      builder.temperature(invocation.temperature().doubleValue());
    }
    if (invocation.webSearch()) {
      builder.webSearchTool(
          AnthropicWebSearchTool.builder().maxUses(invocation.maxWebSearches()).build());
    }
    return builder.build();
  }

  private static ModelResponse toModelResponse(ChatResponse response, Duration duration) {
    String text =
        response.getResults().stream()
            .map(Generation::getOutput)
            .map(message -> message.getText())
            .filter(Objects::nonNull)
            .collect(Collectors.joining());

    long inputTokens = 0;
    long outputTokens = 0;
    long webSearches = 0;
    if (response.getMetadata().getUsage().getNativeUsage() instanceof Usage usage) {
      inputTokens = usage.inputTokens();
      outputTokens = usage.outputTokens();
      webSearches = usage.serverToolUse().map(ServerToolUsage::webSearchRequests).orElse(0L);
    }

    // Spring AI 2.0.3 returns no generation (and no usage) when the answer has no content,
    // e.g. for a refusal; the stop reason is then unknown.
    String stopReason =
        response.getResults().isEmpty()
            ? ModelResponse.NO_CONTENT
            : response.getResults().getLast().getMetadata().getFinishReason();
    return new ModelResponse(text, inputTokens, outputTokens, webSearches, stopReason, duration);
  }
}
