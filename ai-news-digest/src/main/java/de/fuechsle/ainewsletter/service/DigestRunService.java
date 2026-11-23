package de.fuechsle.ainewsletter.service;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.domain.AiModel;
import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import de.fuechsle.ainewsletter.domain.ProviderResultStatus;
import de.fuechsle.ainewsletter.domain.RunStatus;
import de.fuechsle.ainewsletter.domain.Topic;
import de.fuechsle.ainewsletter.domain.TopicState;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.NewsItemRepository;
import de.fuechsle.ainewsletter.repository.ProviderResultRepository;
import de.fuechsle.ainewsletter.repository.TopicRepository;
import de.fuechsle.ainewsletter.repository.TopicStateRepository;
import de.fuechsle.ainewsletter.research.ResearchService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Life cycle of a digest run: prepare, evaluate the research phase, finish. */
@Service
public class DigestRunService {

  private final TopicRepository topics;
  private final TopicStateRepository topicStates;
  private final DigestRunRepository runs;
  private final ProviderResultRepository providerResults;
  private final NewsItemRepository newsItems;
  private final RunPeriodCalculator periods;
  private final ResearchService research;
  private final NewsDigestProperties.Run defaults;
  private final Clock clock;

  public DigestRunService(
      TopicRepository topics,
      TopicStateRepository topicStates,
      DigestRunRepository runs,
      ProviderResultRepository providerResults,
      NewsItemRepository newsItems,
      RunPeriodCalculator periods,
      ResearchService research,
      NewsDigestProperties properties,
      Clock clock) {
    this.topics = topics;
    this.topicStates = topicStates;
    this.runs = runs;
    this.providerResults = providerResults;
    this.newsItems = newsItems;
    this.periods = periods;
    this.research = research;
    this.defaults = properties.run();
    this.clock = clock;
  }

  /**
   * Creates the run (status RUNNING) and determines period and models from the current
   * configuration. A concurrent second run of the topic fails on the partial unique index.
   */
  @Transactional
  public PreparedRun prepare(long topicId, String processInstanceId) {
    Topic topic = topics.findForRun(topicId).orElseThrow(() -> new TopicNotFoundException(topicId));
    LocalDate lastSuccessful =
        topicStates.findById(topicId).map(TopicState::getLastSuccessfulRunAt).orElse(null);
    int lookback =
        topic.getInitialLookbackDays() != null
            ? topic.getInitialLookbackDays()
            : defaults.initialLookbackDays();
    RunPeriod period =
        periods.calculate(ZoneId.of(topic.getSchedule().getTimezone()), lastSuccessful, lookback);

    DigestRun run =
        new DigestRun(
            topic,
            topic.getAggregatorModel(),
            research.promptVersion(),
            period.from(),
            period.to(),
            clock.instant());
    run.setProcessInstanceId(processInstanceId);
    runs.saveAndFlush(run);

    List<Long> modelIds =
        topic.getModels().stream()
            .filter(AiModel::isActive)
            .filter(model -> model.getProvider().isActive())
            .map(AiModel::getId)
            .sorted(Comparator.naturalOrder())
            .toList();
    return new PreparedRun(run.getId(), period, modelIds, topic.getAggregatorModel().getId());
  }

  /** Evaluates the stored results of all research models of the run (after the join). */
  @Transactional(readOnly = true)
  public ResearchOutcome researchOutcome(long runId) {
    if (!providerResults.existsByDigestRunIdAndStatus(runId, ProviderResultStatus.SUCCEEDED)) {
      return ResearchOutcome.NONE_SUCCEEDED;
    }
    if (!newsItems.existsByProviderResultDigestRunIdAndStatus(runId, NewsItemStatus.VALID)) {
      return ResearchOutcome.NO_VALID_NEWS;
    }
    return ResearchOutcome.VALID_NEWS;
  }

  /**
   * Ends the run. For COMPLETED and NO_NEWS the {@code last_successful_run_at} of the topic moves
   * to the end of the run period, so the next run continues from there.
   */
  @Transactional
  public void finish(long runId, RunStatus status) {
    DigestRun run = runs.findById(runId).orElseThrow();
    run.finish(status, clock.instant());
    if (status == RunStatus.COMPLETED || status == RunStatus.NO_NEWS) {
      TopicState state =
          topicStates
              .findById(run.getTopic().getId())
              .orElseGet(() -> new TopicState(run.getTopic()));
      state.setLastSuccessfulRunAt(run.getPeriodTo());
      topicStates.save(state);
    }
  }
}
