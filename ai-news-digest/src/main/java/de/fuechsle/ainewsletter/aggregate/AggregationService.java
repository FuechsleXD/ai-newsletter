package de.fuechsle.ainewsletter.aggregate;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Section;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Source;
import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.cost.CostCalculator;
import de.fuechsle.ainewsletter.prompt.PromptRenderer;
import de.fuechsle.ainewsletter.prompt.PromptTemplate;
import de.fuechsle.ainewsletter.prompt.PromptTemplates;
import de.fuechsle.ainewsletter.provider.JsonModelCall;
import de.fuechsle.ainewsletter.provider.JsonModelCall.Outcome;
import de.fuechsle.ainewsletter.provider.ModelCalls;
import de.fuechsle.ainewsletter.provider.ModelInvocation;
import de.fuechsle.ainewsletter.provider.NewsProviderRegistry;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Lets the aggregator model of the topic condense the checked news items into a {@link
 * DigestAggregate}: render the active aggregate prompt, call the model without web search, parse
 * and validate the JSON answer (with repair attempts, {@link JsonModelCall}) and then set {@code
 * sources} and the per-section {@code models} from the checked news items, never from the model
 * (docs/09-pdf-design.md, "Quellen und Konsens"). Every call is costed, also when the aggregation
 * fails (golden rule 5).
 *
 * <p>Nothing is persisted here; see {@link RunAggregationService}.
 */
@Service
public class AggregationService {

  private static final String NO_DESCRIPTION = "keiner";
  private static final String NO_PREVIOUS_HEADLINES = "none";

  private final NewsProviderRegistry providers;
  private final PromptTemplates templates;
  private final PromptRenderer renderer;
  private final JsonModelCall jsonCall;
  private final AggregateResponseParser parser;
  private final IconKeys iconKeys;
  private final CostCalculator costs;
  private final ObjectMapper objectMapper;
  private final NewsDigestProperties.Ai ai;
  private final NewsDigestProperties.Output output;

  public AggregationService(
      NewsProviderRegistry providers,
      PromptTemplates templates,
      PromptRenderer renderer,
      JsonModelCall jsonCall,
      AggregateResponseParser parser,
      IconKeys iconKeys,
      CostCalculator costs,
      ObjectMapper objectMapper,
      NewsDigestProperties properties) {
    this.providers = providers;
    this.templates = templates;
    this.renderer = renderer;
    this.jsonCall = jsonCall;
    this.parser = parser;
    this.iconKeys = iconKeys;
    this.costs = costs;
    this.objectMapper = objectMapper;
    this.ai = properties.ai();
    this.output = properties.output();
  }

  public AggregationOutcome aggregate(AggregationRequest request) {
    Map<String, AggregationItem> items =
        request.items().stream()
            .collect(
                Collectors.toMap(
                    AggregationItem::id,
                    Function.identity(),
                    (first, second) -> first,
                    LinkedHashMap::new));

    PromptTemplate template = templates.aggregate();
    ModelInvocation invocation =
        ModelInvocation.of(
            request.model(),
            renderer.render(template, values(request)),
            ai.defaultMaxOutputTokens(),
            ai.callTimeout());
    ModelCalls calls = new ModelCalls(providers, costs, request.model());
    Outcome<DigestAggregate> outcome =
        jsonCall.call(
            calls,
            invocation,
            template,
            text -> parser.parse(text, items.keySet(), output.maxTopicCards()));

    DigestAggregate aggregate = outcome.succeeded() ? complete(outcome.value(), items) : null;
    return new AggregationOutcome(
        aggregate,
        calls.inputTokens(),
        calls.outputTokens(),
        calls.cost(),
        calls.duration(),
        outcome.errorMessage(),
        calls.usageEstimated());
  }

  /** Sets sources and per-section models from the checked news items. */
  static DigestAggregate complete(DigestAggregate parsed, Map<String, AggregationItem> items) {
    Map<String, Source> sources = new LinkedHashMap<>();
    List<Section> sections = new ArrayList<>();
    for (Section section : parsed.sections()) {
      Set<String> models = new TreeSet<>();
      for (String id : section.sourceIds()) {
        AggregationItem item = items.get(id);
        models.addAll(item.models());
        sources.putIfAbsent(id, new Source(id, item.headline(), item.url()));
      }
      sections.add(section.withModels(List.copyOf(models)));
    }
    return parsed.withSourcesAndSections(List.copyOf(sources.values()), sections);
  }

  /**
   * The news items as JSON for the data block of the prompt. Angle brackets are escaped (still
   * valid JSON), so text from a research model cannot close the data block ({@code <NEWS>}, in
   * older versions {@code <MELDUNGEN>}).
   */
  String newsItemsJson(List<AggregationItem> items) {
    return objectMapper.writeValueAsString(items).replace("<", "\\u003c").replace(">", "\\u003e");
  }

  /**
   * One heading per line. The headings are earlier model output and stand outside the data block of
   * the prompt: line breaks and angle brackets are removed and each heading is shortened, so they
   * cannot open a block or carry long instructions.
   */
  String previousHeadlines(List<String> headlines) {
    if (!ai.passPreviousHeadlinesToAggregator() || headlines.isEmpty()) {
      return NO_PREVIOUS_HEADLINES;
    }
    return headlines.stream()
        .map(heading -> heading.replaceAll("[<>]", " ").replaceAll("\\s+", " ").strip())
        .filter(heading -> !heading.isEmpty())
        .map(heading ->
            heading.length() > output.maxHeadingChars()
                ? heading.substring(0, output.maxHeadingChars())
                : heading)
        .map(heading -> "- " + heading)
        .collect(Collectors.joining("\n"));
  }

  private Map<String, String> values(AggregationRequest request) {
    String description =
        request.topicDescription() == null || request.topicDescription().isBlank()
            ? NO_DESCRIPTION
            : request.topicDescription();
    return Map.ofEntries(
        Map.entry("topicName", request.topicName()),
        Map.entry("topicDescription", description),
        Map.entry("periodFrom", DateTimeFormatter.ISO_LOCAL_DATE.format(request.periodFrom())),
        Map.entry("periodTo", DateTimeFormatter.ISO_LOCAL_DATE.format(request.periodTo())),
        Map.entry("language", output.language().getDisplayLanguage(Locale.ENGLISH)),
        Map.entry("previousHeadlines", previousHeadlines(request.previousHeadlines())),
        Map.entry("allowedIconKeys", String.join(", ", iconKeys.keys())),
        Map.entry("newsItemsJson", newsItemsJson(request.items())),
        Map.entry("minSections", Integer.toString(output.minTopicCards())),
        Map.entry("maxSections", Integer.toString(output.maxTopicCards())),
        Map.entry("maxHeadingChars", Integer.toString(output.maxHeadingChars())),
        Map.entry("maxTextChars", Integer.toString(output.maxTextChars())));
  }
}
