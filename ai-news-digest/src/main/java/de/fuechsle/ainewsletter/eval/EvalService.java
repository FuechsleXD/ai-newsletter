package de.fuechsle.ainewsletter.eval;

import de.fuechsle.ainewsletter.domain.Topic;
import de.fuechsle.ainewsletter.process.DigestProcessStarter;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.TopicRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Prompt evaluation (docs/10-tests.md, "Bewertung von Prompts"): runs the configured topics one
 * after another through the normal process, so costs are recorded and the budget is checked like
 * for every run (golden rule 5), waits for the end of each run and exports its results with {@link
 * EvalExporter} to {@code <output>/<prompt-version>/<time>-<topic>/}. Mails go to the recipients of
 * the topic as usual; eval topics should only have Mailpit or own addresses.
 */
@Service
public class EvalService {

  private static final Logger log = LoggerFactory.getLogger(EvalService.class);
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

  private final TopicRepository topics;
  private final DigestRunRepository runs;
  private final DigestProcessStarter starter;
  private final EvalExporter exporter;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public EvalService(
      TopicRepository topics,
      DigestRunRepository runs,
      DigestProcessStarter starter,
      EvalExporter exporter,
      JdbcTemplate jdbc,
      Clock clock) {
    this.topics = topics;
    this.runs = runs;
    this.starter = starter;
    this.exporter = exporter;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /**
   * Runs and exports every topic; a topic that does not exist or cannot be started is skipped.
   *
   * @return the result of every topic in the given order
   */
  public List<EvalResult> run(List<String> topicNames, Path outputDirectory, Duration timeout) {
    List<EvalResult> results = new ArrayList<>();
    for (String topicName : topicNames) {
      results.add(run(topicName, outputDirectory, timeout));
    }
    return results;
  }

  private EvalResult run(String topicName, Path outputDirectory, Duration timeout) {
    Optional<Topic> topic = topics.findByName(topicName);
    if (topic.isEmpty()) {
      log.warn("Eval topic '{}' does not exist, skipped", topicName);
      return EvalResult.skipped(topicName, "Topic existiert nicht");
    }
    String processInstanceId;
    try {
      processInstanceId = starter.start(topic.get().getId());
    } catch (RuntimeException e) {
      log.warn("Eval topic '{}' cannot be started: {}", topicName, e.getMessage());
      return EvalResult.skipped(topicName, e.getMessage());
    }
    long runId = runs.findByProcessInstanceId(processInstanceId).orElseThrow().getId();
    String status = awaitEnd(runId, timeout);
    String promptVersion =
        jdbc.queryForObject(
            "SELECT prompt_version FROM digest_run WHERE id = ?", String.class, runId);
    Path directory =
        outputDirectory
            .resolve(promptVersion)
            .resolve(TIME.format(clock.instant()) + "-" + EvalExporter.slug(topicName));
    exporter.export(runId, directory);
    log.info(
        "Eval run {} of topic '{}' ended with {}, exported to {}",
        runId,
        topicName,
        status,
        directory);
    return new EvalResult(topicName, runId, status, directory, null);
  }

  /** Status of the run at its end, or {@code RUNNING} if it did not end in time. */
  private String awaitEnd(long runId, Duration timeout) {
    Instant deadline = clock.instant().plus(timeout);
    String status = status(runId);
    while ("RUNNING".equals(status) && clock.instant().isBefore(deadline)) {
      try {
        Thread.sleep(POLL_INTERVAL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return status;
      }
      status = status(runId);
    }
    return status;
  }

  private String status(long runId) {
    return jdbc.queryForObject("SELECT status FROM digest_run WHERE id = ?", String.class, runId);
  }

  /**
   * Result of one eval topic.
   *
   * @param runId {@code null} if the topic was skipped
   * @param status status of the run at the end of waiting ({@code RUNNING} after a timeout)
   * @param directory export directory, {@code null} if skipped
   * @param skipReason why the topic was skipped, {@code null} otherwise
   */
  public record EvalResult(
      String topic, Long runId, String status, Path directory, String skipReason) {

    static EvalResult skipped(String topic, String reason) {
      return new EvalResult(topic, null, null, null, reason);
    }
  }
}
