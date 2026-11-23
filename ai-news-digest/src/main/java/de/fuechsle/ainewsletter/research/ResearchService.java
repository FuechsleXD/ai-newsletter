package de.fuechsle.ainewsletter.research;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.cost.CostCalculator;
import de.fuechsle.ainewsletter.domain.ProviderResultStatus;
import de.fuechsle.ainewsletter.prompt.PromptRenderer;
import de.fuechsle.ainewsletter.prompt.PromptTemplate;
import de.fuechsle.ainewsletter.prompt.PromptTemplates;
import de.fuechsle.ainewsletter.provider.ModelInvocation;
import de.fuechsle.ainewsletter.provider.ModelResponse;
import de.fuechsle.ainewsletter.provider.NewsProviderRegistry;
import de.fuechsle.ainewsletter.provider.ProviderException;
import de.fuechsle.ainewsletter.secret.SecretNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the research of one model for one topic and period: render the active research prompt, call
 * the model with web search, parse the JSON answer and, if it is not valid, make the configured
 * number of repair attempts with the same model (without web search). Every call is costed, also
 * when the result is {@code FAILED} (golden rule 5).
 *
 * <p>Nothing is persisted here; the process stores the result as {@code provider_result}.
 */
@Service
public class ResearchService {

  private static final Logger log = LoggerFactory.getLogger(ResearchService.class);
  private static final String NO_DESCRIPTION = "keiner";
  private static final String OUTPUT_SECTION = "AUSGABE";

  private final NewsProviderRegistry providers;
  private final PromptTemplates templates;
  private final PromptRenderer renderer;
  private final ResearchResponseParser parser;
  private final CostCalculator costs;
  private final NewsDigestProperties.Ai ai;
  private final Locale language;

  public ResearchService(
      NewsProviderRegistry providers,
      PromptTemplates templates,
      PromptRenderer renderer,
      ResearchResponseParser parser,
      CostCalculator costs,
      NewsDigestProperties properties) {
    this.providers = providers;
    this.templates = templates;
    this.renderer = renderer;
    this.parser = parser;
    this.costs = costs;
    this.ai = properties.ai();
    this.language = properties.output().language();
  }

  /** Version of the research prompt in use, stored in {@code digest_run.prompt_version}. */
  public String promptVersion() {
    return templates.research().version();
  }

  public ResearchResult research(ResearchRequest request) {
    ResearchModel model = request.model();
    if (!model.supportsWebSearch()) {
      return failed("Modell " + model.modelName() + " unterstützt keine Websuche");
    }
    if (model.prices().perSearchCall() == null) {
      return failed("Für Modell " + model.modelName() + " ist kein Preis pro Websuche gepflegt");
    }

    PromptTemplate template = templates.research();
    ModelInvocation invocation = invocation(model, renderer.render(template, values(request)));
    Calls calls = new Calls(model);

    ModelResponse response = calls.call(invocation);
    if (response == null) {
      return calls.failed(calls.lastError);
    }
    if (response.refused()) {
      return calls.failed("Modell hat die Anfrage abgelehnt");
    }
    if (response.noContent()) {
      return calls.failed("Modell hat keine Antwort geliefert (möglicherweise abgelehnt)");
    }

    String parseError;
    try {
      return calls.succeeded(parser.parse(response.text(), ai.maxItemsPerModel()));
    } catch (ResearchParseException e) {
      parseError = e.getMessage();
    }

    for (int attempt = 1; attempt <= ai.jsonRepairAttempts(); attempt++) {
      log.info(
          "Research answer of model {} is not valid ({}), repair attempt {}",
          model.modelName(),
          parseError,
          attempt);
      String repairPrompt =
          renderer.render(
              templates.repairJson(),
              Map.of(
                  "parseError",
                  parseError,
                  "expectedFormat",
                  expectedFormat(template),
                  "previousResponse",
                  calls.lastText));
      response = calls.call(invocation.withPrompt(repairPrompt).withoutWebSearch());
      if (response == null) {
        return calls.failed(calls.lastError);
      }
      try {
        return calls.succeeded(parser.parse(response.text(), ai.maxItemsPerModel()));
      } catch (ResearchParseException e) {
        parseError = e.getMessage();
      }
    }
    return calls.failed("Antwort ist kein gültiges JSON: " + parseError);
  }

  private ModelInvocation invocation(ResearchModel model, String prompt) {
    int maxTokens =
        model.maxOutputTokens() != null ? model.maxOutputTokens() : ai.defaultMaxOutputTokens();
    return new ModelInvocation(
        model.providerCode(),
        model.secretName(),
        model.modelName(),
        maxTokens,
        model.temperature(),
        true,
        ai.maxWebSearchesPerCall(),
        prompt,
        ai.callTimeout());
  }

  private Map<String, String> values(ResearchRequest request) {
    String description =
        request.topicDescription() == null || request.topicDescription().isBlank()
            ? NO_DESCRIPTION
            : request.topicDescription();
    return Map.of(
        "topicName", request.topicName(),
        "topicDescription", description,
        "periodFrom", DateTimeFormatter.ISO_LOCAL_DATE.format(request.periodFrom()),
        "periodTo", DateTimeFormatter.ISO_LOCAL_DATE.format(request.periodTo()),
        "timezone", request.timezone().getId(),
        "maxItems", Integer.toString(ai.maxItemsPerModel()),
        "language", language.getDisplayLanguage(Locale.GERMAN));
  }

  /** The output section of the research prompt, shown again in the repair prompt. */
  static String expectedFormat(PromptTemplate template) {
    int index = template.text().indexOf(OUTPUT_SECTION);
    return index < 0 ? template.text() : template.text().substring(index).strip();
  }

  private static ResearchResult failed(String message) {
    return new ResearchResult(
        ProviderResultStatus.FAILED,
        List.of(),
        null,
        0,
        0,
        0,
        BigDecimal.ZERO.setScale(CostCalculator.SCALE),
        Duration.ZERO,
        message);
  }

  /** Accumulates usage, cost and duration over the research call and the repair attempts. */
  private final class Calls {

    private final ResearchModel model;
    private final List<ModelResponse> responses = new ArrayList<>();
    private String lastText;
    private String lastError;

    private Calls(ResearchModel model) {
      this.model = model;
    }

    /** Returns the response, or {@code null} if the call failed ({@link #lastError} is set). */
    private ModelResponse call(ModelInvocation invocation) {
      try {
        ModelResponse response = providers.get(model.providerCode()).call(invocation);
        responses.add(response);
        lastText = response.text();
        return response;
      } catch (ProviderException | SecretNotFoundException e) {
        log.warn("Research call of model {} failed: {}", model.modelName(), e.getMessage());
        lastError = e.getMessage();
        return null;
      }
    }

    private ResearchResult succeeded(ResearchResponse parsed) {
      if (parsed.skippedItems() > 0) {
        log.info(
            "Model {} returned {} item(s) that break the contract or exceed the limit",
            model.modelName(),
            parsed.skippedItems());
      }
      return result(ProviderResultStatus.SUCCEEDED, parsed.items(), null);
    }

    private ResearchResult failed(String message) {
      return result(ProviderResultStatus.FAILED, List.of(), message);
    }

    private ResearchResult result(
        ProviderResultStatus status, List<NewsItemCandidate> items, String error) {
      long input = responses.stream().mapToLong(ModelResponse::inputTokens).sum();
      long output = responses.stream().mapToLong(ModelResponse::outputTokens).sum();
      long searches = responses.stream().mapToLong(ModelResponse::webSearches).sum();
      Duration duration =
          responses.stream().map(ModelResponse::duration).reduce(Duration.ZERO, Duration::plus);
      BigDecimal cost = costs.cost(model.prices(), input, output, searches);
      return new ResearchResult(
          status, items, lastText, input, output, searches, cost, duration, error);
    }
  }
}
