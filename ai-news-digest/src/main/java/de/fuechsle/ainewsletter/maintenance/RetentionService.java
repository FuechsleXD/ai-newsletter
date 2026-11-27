package de.fuechsle.ainewsletter.maintenance;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.cibseven.bpm.engine.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the retention periods (docs/12-defaults.md): raw answers of the models are cleared after
 * {@code newsdigest.retention.raw-response}, finished runs with their results, news items and mail
 * log are deleted after {@code newsdigest.retention.runs} (foreign keys with {@code ON DELETE
 * CASCADE}), and the history cleanup of the engine removes process history after its {@code
 * historyTimeToLive}. PDFs in S3 expire by the lifecycle rule of the bucket.
 */
@Service
public class RetentionService {

  private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

  private final JdbcTemplate jdbc;
  private final HistoryService history;
  private final Clock clock;
  private final NewsDigestProperties.Retention retention;

  public RetentionService(
      JdbcTemplate jdbc, HistoryService history, Clock clock, NewsDigestProperties properties) {
    this.jdbc = jdbc;
    this.history = history;
    this.clock = clock;
    this.retention = properties.retention();
  }

  /** Clears and deletes what is older than the retention periods; running runs stay untouched. */
  @Transactional
  public Result cleanUp() {
    Instant rawCutoff = cutoff(retention.rawResponse());
    Instant runCutoff = cutoff(retention.runs());
    int clearedRawResponses =
        jdbc.update(
            "UPDATE provider_result SET raw_response = NULL"
                + " WHERE raw_response IS NOT NULL AND digest_run_id IN"
                + " (SELECT id FROM digest_run WHERE started_at < ? AND status <> 'RUNNING')",
            Timestamp.from(rawCutoff));
    int deletedRuns =
        jdbc.update(
            "DELETE FROM digest_run WHERE started_at < ? AND status <> 'RUNNING'",
            Timestamp.from(runCutoff));
    int deletedWarnings =
        jdbc.update(
            "DELETE FROM budget_warning WHERE month < ?",
            runCutoff.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1));
    Result result = new Result(clearedRawResponses, deletedRuns, deletedWarnings);
    log.info(
        "Retention: {} raw answers cleared, {} runs and {} budget warnings deleted",
        result.clearedRawResponses(),
        result.deletedRuns(),
        result.deletedBudgetWarnings());
    return result;
  }

  /**
   * Starts the history cleanup of the engine once, right away; the job executor runs it. Without a
   * batch window the engine does not repeat it by itself, the maintenance job starts it again.
   */
  public void startHistoryCleanup() {
    history.cleanUpHistoryAsync(true);
  }

  private Instant cutoff(Period period) {
    return ZonedDateTime.now(clock.withZone(ZoneOffset.UTC)).minus(period).toInstant();
  }

  /** Numbers of one cleanup. */
  public record Result(int clearedRawResponses, int deletedRuns, int deletedBudgetWarnings) {}
}
