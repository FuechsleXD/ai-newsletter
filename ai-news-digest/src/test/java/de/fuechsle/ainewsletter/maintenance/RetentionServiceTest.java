package de.fuechsle.ainewsletter.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Retention periods against the real schema (defaults: raw answers 90 days, runs 2 years). */
class RetentionServiceTest extends ProcessTestSupport {

  @Autowired private RetentionService retention;

  @Test
  void rawAnswersAreClearedAndOldRunsDeletedAtTheirLimits() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Aufbewahrung " + suffix, model, model);
    long otherTopic = topic("Aufbewahrung läuft " + suffix, model, model);

    long recent = run(topic, ago(Period.ofDays(89)), "COMPLETED", model);
    long oldAnswer = run(topic, ago(Period.ofDays(91)), "COMPLETED", model);
    long almostExpired = run(topic, ago(Period.ofYears(2).minusDays(1)), "FAILED", model);
    long expired = run(topic, ago(Period.ofYears(2).plusDays(1)), "NO_NEWS", model);
    long expiredButRunning = run(otherTopic, ago(Period.ofYears(3)), "RUNNING", model);
    LocalDate oldMonth = LocalDate.now(ZoneOffset.UTC).minusYears(3).withDayOfMonth(1);
    jdbc.update("INSERT INTO budget_warning (month) VALUES (?) ON CONFLICT DO NOTHING", oldMonth);

    RetentionService.Result result = retention.cleanUp();

    assertThat(rawResponse(recent)).isEqualTo("raw");
    assertThat(rawResponse(oldAnswer)).isNull();
    assertThat(rawResponse(almostExpired)).isNull();
    assertThat(exists(almostExpired)).isTrue();
    assertThat(exists(expired)).isFalse();
    // Results and news items go with the run (ON DELETE CASCADE)
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM provider_result WHERE digest_run_id = ?",
                Integer.class,
                expired))
        .isZero();
    // A running run is left to the cancellation of overdue runs
    assertThat(exists(expiredButRunning)).isTrue();
    assertThat(rawResponse(expiredButRunning)).isEqualTo("raw");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM budget_warning WHERE month = ?", Integer.class, oldMonth))
        .isZero();
    assertThat(result.deletedRuns()).isPositive();
    assertThat(result.clearedRawResponses()).isGreaterThanOrEqualTo(2);
    assertThat(result.deletedBudgetWarnings()).isPositive();
  }

  @Test
  void historyCleanupOfTheEngineIsStarted() {
    retention.startHistoryCleanup();

    assertThat(historyService.findHistoryCleanupJobs()).isNotEmpty();
  }

  private long run(long topicId, Instant startedAt, String status, long modelId) {
    Timestamp started = Timestamp.from(startedAt);
    long runId =
        jdbc.queryForObject(
            "INSERT INTO digest_run (topic_id, aggregator_model_id, prompt_version, period_from,"
                + " period_to, status, started_at, finished_at)"
                + " VALUES (?, ?, 'test', ?, ?, ?, ?, ?) RETURNING id",
            Long.class,
            topicId,
            modelId,
            LocalDate.ofInstant(startedAt, ZoneOffset.UTC).minusDays(7),
            LocalDate.ofInstant(startedAt, ZoneOffset.UTC),
            status,
            started,
            status.equals("RUNNING") ? null : started);
    long resultId =
        jdbc.queryForObject(
            "INSERT INTO provider_result (digest_run_id, model_id, status, raw_response,"
                + " duration_ms) VALUES (?, ?, 'SUCCEEDED', 'raw', 1) RETURNING id",
            Long.class,
            runId,
            modelId);
    jdbc.update(
        "INSERT INTO news_item (provider_result_id, headline, summary, url, normalized_url,"
            + " status) VALUES (?, 'h', 's', 'https://example.test/a', 'https://example.test/a',"
            + " 'VALID')",
        resultId);
    return runId;
  }

  private String rawResponse(long runId) {
    return jdbc.queryForObject(
        "SELECT raw_response FROM provider_result WHERE digest_run_id = ?", String.class, runId);
  }

  private boolean exists(long runId) {
    return jdbc.queryForObject("SELECT count(*) FROM digest_run WHERE id = ?", Integer.class, runId)
        == 1;
  }

  private static Instant ago(Period period) {
    return ZonedDateTime.now(ZoneOffset.UTC).minus(period).toInstant();
  }
}
