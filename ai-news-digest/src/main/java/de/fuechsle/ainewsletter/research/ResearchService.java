package de.fuechsle.ainewsletter.research;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.cost.CostCalculator;
import de.fuechsle.ainewsletter.domain.ProviderResultStatus;
import de.fuechsle.ainewsletter.prompt.PromptRenderer;
import de.fuechsle.ainewsletter.prompt.PromptTemplate;
import de.fuechsle.ainewsletter.prompt.PromptTemplates;
import de.fuechsle.ainewsletter.provider.JsonModelCall;
import de.fuechsle.ainewsletter.provider.JsonModelCall.Outcome;
import de.fuechsle.ainewsletter.provider.ModelCalls;
import de.fuechsle.ainewsletter.provider.ModelInvocation;
import de.fuechsle.ainewsletter.provider.ModelSnapshot;
import de.fuechsle.ainewsletter.provider.NewsProviderRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the research of one model for one topic and period: render the active research prompt, call
 * the model with web search and parse the JSON answer, with repair attempts if it is not valid
 * ({@link JsonModelCall}). Every call is costed, also when the result is {@code FAILED} (golden
 * rule 5).
 *
 * <p>Nothing is persisted here; the process stores the result as {@code provider_result}.
 */
@Service
public class ResearchService {

  private static final Logger log = LoggerFactory.getLogger(ResearchService.class);
  private static final String NO_DESCRIPTION = "keiner";

  private final NewsProviderRegistry providers;
  private final PromptTemplates templates;
  private final PromptRenderer renderer;
  private final JsonModelCall jsonCall;
  private final ResearchResponseParser parser;
  private final CostCalculator costs;
  private final NewsDigestProperties.Ai ai;
  private final Locale language;

  public ResearchService(
      NewsProviderRegistry providers,
      PromptTemplates templates,
      PromptRenderer renderer,
      JsonModelCall jsonCall,
      ResearchResponseParser parser,
      CostCalculator costs,
      NewsDigestProperties properties) {
    this.providers = providers;
    this.templates = templates;
    this.renderer = renderer;
    this.jsonCall = jsonCall;
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
    ModelSnapshot model = request.model();
    if (!model.supportsWebSearch()) {
      return rejected("Modell " + model.modelName() + " unterstützt keine Websuche");
    }
    if (model.prices().perSearchCall() == null) {
      return rejected("Für Modell " + model.modelName() + " ist kein Preis pro Websuche gepflegt");
    }

    PromptTemplate template = templates.research();
    ModelInvocation invocation =
        ModelInvocation.of(
                model,
                renderer.render(template, values(request)),
                ai.defaultMaxOutputTokens(),
                ai.callTimeout())
            .withWebSearch(ai.maxWebSearchesPerCall());
    ModelCalls calls = new ModelCalls(providers, costs, model);
    Outcome<ResearchResponse> outcome =
        jsonCall.call(
            calls, invocation, template, text -> parser.parse(text, ai.maxItemsPerModel()));

    if (!outcome.succeeded()) {
      return result(calls, ProviderResultStatus.FAILED, List.of(), outcome.errorMessage());
    }
    ResearchResponse parsed = outcome.value();
    if (parsed.skippedItems() > 0) {
      log.info(
          "Model {} returned {} item(s) that break the contract or exceed the limit",
          model.modelName(),
          parsed.skippedItems());
    }
    return result(calls, ProviderResultStatus.SUCCEEDED, parsed.items(), null);
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
        "language", language.getDisplayLanguage(Locale.ENGLISH));
  }

  private static ResearchResult result(
      ModelCalls calls, ProviderResultStatus status, List<NewsItemCandidate> items, String error) {
    return new ResearchResult(
        status,
        items,
        calls.lastText(),
        calls.inputTokens(),
        calls.outputTokens(),
        calls.webSearches(),
        calls.cost(),
        calls.duration(),
        error);
  }

  /** The model is not suitable for research; no call was made. */
  private static ResearchResult rejected(String message) {
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
}
