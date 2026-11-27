package de.fuechsle.ainewsletter.provider;

import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.ServerToolUsage;
import com.anthropic.models.messages.ServerToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.models.messages.WebSearchToolResultBlock;
import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.secret.SecretProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /** Metadata key under which Spring AI keeps the complete Anthropic answer. */
  static final String RESPONSE_METADATA = "anthropic-response";

  /** Conservative characters per token for the estimate of answers without content. */
  static final int CHARS_PER_TOKEN = 3;

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
    if (response.getResults().isEmpty()) {
      return withoutContent(invocation, duration);
    }
    return toModelResponse(response, duration);
  }

  AnthropicChatOptions options(ModelInvocation invocation, String apiKey) {
    AnthropicChatOptions.Builder builder =
        AnthropicChatOptions.builder()
            .model(invocation.modelName())
            .maxTokens(invocation.maxOutputTokens())
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .timeout(invocation.timeout())
            // No hidden SDK retries (Spring AI default: 2): a timed-out attempt is still billed by
            // the provider without its tokens reaching us, and three attempts would outlast the
            // job lock. Retries are left to the engine, visible and costed (ADR-014).
            .maxRetries(0);
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

    String stopReason = response.getResults().getLast().getMetadata().getFinishReason();
    return new ModelResponse(
        text,
        inputTokens,
        outputTokens,
        webSearches,
        stopReason,
        duration,
        false,
        searchTrace(response));
  }

  /**
   * Searches and their results from the complete Anthropic answer, which Spring AI keeps in the
   * metadata ({@value #RESPONSE_METADATA}): every {@code server_tool_use} block named {@code
   * web_search} carries the query, the {@code web_search_tool_result} block with the same tool use
   * ID the pages or an error code. Other server tool calls (e.g. the code execution with which the
   * search tool filters its results) are only counted. Diagnosis only: if the answer is not
   * available, the trace is empty and the call goes on.
   */
  static SearchTrace searchTrace(ChatResponse response) {
    if (!(response.getMetadata().get(RESPONSE_METADATA) instanceof Message message)) {
      return SearchTrace.EMPTY;
    }
    Map<String, String> queries = new LinkedHashMap<>();
    Map<String, SearchTrace.Search> results = new HashMap<>();
    int otherToolCalls = 0;
    for (ContentBlock block : message.content()) {
      if (block.isServerToolUse()) {
        ServerToolUseBlock use = block.asServerToolUse();
        if (ServerToolUseBlock.Name.WEB_SEARCH.equals(use.name())) {
          queries.put(use.id(), query(use));
        } else {
          otherToolCalls++;
        }
      } else if (block.isWebSearchToolResult()) {
        WebSearchToolResultBlock result = block.asWebSearchToolResult();
        if (queries.containsKey(result.toolUseId())) {
          results.put(result.toolUseId(), search(queries.get(result.toolUseId()), result));
        }
      }
    }
    List<SearchTrace.Search> searches = new ArrayList<>();
    queries.forEach((id, query) ->
        searches.add(results.getOrDefault(id, new SearchTrace.Search(query, List.of(), null))));
    return new SearchTrace(searches, otherToolCalls);
  }

  private static String query(ServerToolUseBlock use) {
    try {
      Object query = use._input().convert(Map.class).get("query");
      return query == null ? "" : query.toString();
    } catch (RuntimeException e) {
      return "";
    }
  }

  private static SearchTrace.Search search(String text, WebSearchToolResultBlock result) {
    if (result.content().isError()) {
      return new SearchTrace.Search(
          text, List.of(), result.content().asError().errorCode().toString());
    }
    List<SearchTrace.Hit> hits =
        result.content().asResultBlocks().stream()
            .map(hit -> new SearchTrace.Hit(hit.title(), hit.url(), hit.pageAge().orElse(null)))
            .toList();
    return new SearchTrace.Search(text, hits, null);
  }

  /**
   * Spring AI 2.0.3 returns no generation, no stop reason and no usage when the answer has no
   * content, e.g. for a refusal (ADR-014). The billed input tokens are estimated from the prompt so
   * the call is still costed (golden rule 5): one token per {@value #CHARS_PER_TOKEN} characters,
   * rounded up, which is rather too high than too low for German and English text. Output tokens
   * and web searches are unknown and counted as 0.
   */
  static ModelResponse withoutContent(ModelInvocation invocation, Duration duration) {
    long estimatedInputTokens =
        (invocation.prompt().length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    return new ModelResponse(
        "", estimatedInputTokens, 0, 0, ModelResponse.NO_CONTENT, duration, true);
  }
}
