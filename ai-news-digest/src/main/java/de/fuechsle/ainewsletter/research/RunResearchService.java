package de.fuechsle.ainewsletter.research;

import de.fuechsle.ainewsletter.domain.AiModel;
import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.NewsItem;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import de.fuechsle.ainewsletter.domain.ProviderResult;
import de.fuechsle.ainewsletter.provider.ModelSnapshot;
import de.fuechsle.ainewsletter.repository.AiModelRepository;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.NewsItemRepository;
import de.fuechsle.ainewsletter.repository.ProviderResultRepository;
import java.math.BigDecimal;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the research of one model for one run and stores the result as {@code provider_result} with
 * its {@code news_item} rows.
 *
 * <p>The result is stored in its own transaction right after the model call, so a paid call is
 * recorded even if the surrounding job fails later and is retried. A repeated research of the same
 * run and model replaces the news items of the existing row but adds tokens, searches, cost and
 * duration to it, because every call was billed (docs/04-prozess-bpmn.md, "Idempotenz").
 */
@Service
public class RunResearchService {

  private final DigestRunRepository runs;
  private final AiModelRepository models;
  private final ProviderResultRepository providerResults;
  private final NewsItemRepository newsItems;
  private final ResearchService research;
  private final TransactionTemplate newTransaction;

  public RunResearchService(
      DigestRunRepository runs,
      AiModelRepository models,
      ProviderResultRepository providerResults,
      NewsItemRepository newsItems,
      ResearchService research,
      PlatformTransactionManager transactionManager) {
    this.runs = runs;
    this.models = models;
    this.providerResults = providerResults;
    this.newsItems = newsItems;
    this.research = research;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Researches with one model and stores the result. Model errors end as a {@code FAILED} row, not
   * as an exception.
   *
   * @return id of the stored {@code provider_result}
   */
  public long research(long runId, long modelId) {
    ResearchRequest request = newTransaction.execute(status -> request(runId, modelId));
    ResearchResult result = research.research(request);
    return newTransaction.execute(status -> store(runId, modelId, result));
  }

  private ResearchRequest request(long runId, long modelId) {
    DigestRun run = runs.findWithTopic(runId).orElseThrow();
    AiModel model = models.findWithProvider(modelId).orElseThrow();
    return new ResearchRequest(
        run.getTopic().getName(),
        run.getTopic().getDescription(),
        run.getPeriodFrom(),
        run.getPeriodTo(),
        ZoneId.of(run.getTopic().getSchedule().getTimezone()),
        ModelSnapshot.of(model));
  }

  private long store(long runId, long modelId, ResearchResult result) {
    ProviderResult row =
        providerResults
            .findByDigestRunIdAndModelId(runId, modelId)
            .orElseGet(() ->
                new ProviderResult(
                    runs.getReferenceById(runId),
                    models.getReferenceById(modelId),
                    result.status(),
                    0));
    if (row.getId() != null) {
      newsItems.deleteByProviderResultId(row.getId());
    }

    row.setStatus(result.status());
    row.setRawResponse(result.rawResponse());
    row.setSearchTrace(result.searchTrace().toJson());
    row.setErrorMessage(result.errorMessage());
    row.setDurationMs(row.getDurationMs() + result.duration().toMillis());
    row.setInputTokens(sum(row.getInputTokens(), result.inputTokens()));
    row.setOutputTokens(sum(row.getOutputTokens(), result.outputTokens()));
    row.setSearchCalls(sum(row.getSearchCalls(), result.webSearches()));
    BigDecimal previousCost = row.getCostUsd() == null ? BigDecimal.ZERO : row.getCostUsd();
    row.setCostUsd(previousCost.add(result.costUsd()));
    ProviderResult saved = providerResults.save(row);

    for (NewsItemCandidate candidate : result.items()) {
      // Stored as VALID; the checks after the join (NewsItemChecker) set the final status.
      newsItems.save(
          new NewsItem(
              saved,
              candidate.headline(),
              candidate.summary(),
              candidate.url(),
              UrlNormalizer.normalize(candidate.url()),
              candidate.publishedAt(),
              NewsItemStatus.VALID));
    }
    return saved.getId();
  }

  private static Integer sum(Integer previous, long added) {
    return Math.toIntExact((previous == null ? 0 : previous) + added);
  }
}
