package de.fuechsle.ainewsletter.aggregate;

import de.fuechsle.ainewsletter.domain.AggregationResult;
import de.fuechsle.ainewsletter.domain.AiModel;
import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.NewsItem;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import de.fuechsle.ainewsletter.domain.RunStatus;
import de.fuechsle.ainewsletter.provider.ModelSnapshot;
import de.fuechsle.ainewsletter.repository.AggregationResultRepository;
import de.fuechsle.ainewsletter.repository.AiModelRepository;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.NewsItemRepository;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the aggregation of a run and stores the result: the digest content as {@code
 * digest_run.aggregate_json}, usage and cost as {@code aggregation_result}.
 *
 * <p>The result is stored in its own transaction right after the model call, so a paid call is
 * recorded even if the job fails later. A repeated aggregation replaces the content but adds
 * tokens, cost and duration, because every call was billed (docs/04-prozess-bpmn.md, "Idempotenz").
 */
@Service
public class RunAggregationService {

  private static final Logger log = LoggerFactory.getLogger(RunAggregationService.class);

  private final DigestRunRepository runs;
  private final AiModelRepository models;
  private final NewsItemRepository newsItems;
  private final AggregationResultRepository aggregationResults;
  private final AggregationService aggregation;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate newTransaction;

  public RunAggregationService(
      DigestRunRepository runs,
      AiModelRepository models,
      NewsItemRepository newsItems,
      AggregationResultRepository aggregationResults,
      AggregationService aggregation,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.runs = runs;
    this.models = models;
    this.newsItems = newsItems;
    this.aggregationResults = aggregationResults;
    this.aggregation = aggregation;
    this.objectMapper = objectMapper;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Aggregates the valid news items of the run and stores the result. Model errors and invalid
   * answers do not throw; they end as a failed outcome with the reason also stored at the run.
   */
  public AggregationOutcome aggregate(long runId) {
    AggregationRequest request = newTransaction.execute(status -> request(runId));
    AggregationOutcome outcome = aggregation.aggregate(request);
    newTransaction.executeWithoutResult(status -> store(runId, request.model(), outcome));
    return outcome;
  }

  private AggregationRequest request(long runId) {
    DigestRun run = runs.findWithTopic(runId).orElseThrow();
    AiModel aggregator = models.findWithProvider(run.getAggregatorModel().getId()).orElseThrow();
    return new AggregationRequest(
        run.getTopic().getName(),
        run.getTopic().getDescription(),
        run.getPeriodFrom(),
        run.getPeriodTo(),
        ModelSnapshot.of(aggregator),
        items(newsItems.findByRun(runId)),
        previousHeadlines(run));
  }

  /** Section headings of the last completed run of the topic, empty if there is none. */
  private List<String> previousHeadlines(DigestRun run) {
    return runs.findFirstByTopicIdAndStatusAndIdNotOrderByStartedAtDesc(
            run.getTopic().getId(), RunStatus.COMPLETED, run.getId())
        .map(DigestRun::getAggregateJson)
        .map(this::headings)
        .orElse(List.of());
  }

  private List<String> headings(String aggregateJson) {
    try {
      return objectMapper.readValue(aggregateJson, DigestAggregate.class).sections().stream()
          .map(DigestAggregate.Section::heading)
          .filter(Objects::nonNull)
          .toList();
    } catch (JacksonException e) {
      // The headlines only help to avoid repetitions; a broken old aggregate does not stop the run
      log.warn("Aggregate of an earlier run could not be read: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * The valid items with the models of all copies of the same URL in the run (an item that is a
   * duplicate within the run still counts for the consensus).
   */
  static List<AggregationItem> items(List<NewsItem> runItems) {
    Map<String, TreeSet<String>> modelsByUrl =
        runItems.stream()
            .filter(item ->
                item.getStatus() == NewsItemStatus.VALID
                    || item.getStatus() == NewsItemStatus.DUPLICATE_RUN)
            .collect(
                Collectors.groupingBy(
                    NewsItem::getNormalizedUrl,
                    Collectors.mapping(
                        item -> item.getProviderResult().getModel().getDisplayName(),
                        Collectors.toCollection(TreeSet::new))));
    return runItems.stream()
        .filter(item -> item.getStatus() == NewsItemStatus.VALID)
        .map(item ->
            new AggregationItem(
                String.valueOf(item.getId()),
                item.getHeadline(),
                item.getSummary(),
                item.getUrl(),
                item.getPublishedAt() == null
                    ? null
                    : DateTimeFormatter.ISO_LOCAL_DATE.format(item.getPublishedAt()),
                List.copyOf(modelsByUrl.get(item.getNormalizedUrl()))))
        .toList();
  }

  private void store(long runId, ModelSnapshot model, AggregationOutcome outcome) {
    int input = Math.toIntExact(outcome.inputTokens());
    int output = Math.toIntExact(outcome.outputTokens());
    long durationMs = outcome.duration().toMillis();
    AggregationResult result =
        aggregationResults
            .findById(runId)
            .map(existing -> {
              existing.addUsage(input, output, outcome.costUsd(), durationMs);
              return existing;
            })
            .orElseGet(() ->
                aggregationResults.save(
                    new AggregationResult(
                        runs.getReferenceById(runId),
                        models.getReferenceById(model.modelId()),
                        input,
                        output,
                        outcome.costUsd(),
                        durationMs)));
    result.markCostEstimated(outcome.costEstimated());

    DigestRun run = runs.findById(runId).orElseThrow();
    if (outcome.succeeded()) {
      run.setAggregateJson(objectMapper.writeValueAsString(outcome.aggregate()));
      run.setErrorMessage(null);
    } else {
      run.setAggregateJson(null);
      run.setErrorMessage("Aggregation fehlgeschlagen: " + outcome.errorMessage());
    }
  }
}
