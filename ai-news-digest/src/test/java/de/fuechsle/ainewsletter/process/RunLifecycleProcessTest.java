package de.fuechsle.ainewsletter.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.mail.OperatorNotificationService;
import de.fuechsle.ainewsletter.maintenance.OverdueRunCanceller;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.TopicRepository;
import de.fuechsle.ainewsletter.scheduling.SchedulerTestAccess;
import de.fuechsle.ainewsletter.service.RunCancellationService;
import de.fuechsle.ainewsletter.service.RunNotFoundException;
import de.fuechsle.ainewsletter.service.RunNotRunningException;
import de.fuechsle.ainewsletter.support.Mailpit;
import de.fuechsle.ainewsletter.support.PostgresTestSupport;
import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.cibseven.bpm.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Scheduled start, cancellation and overdue runs with the real process (fake provider). */
class RunLifecycleProcessTest extends ProcessTestSupport {

  @Autowired private DigestProcessStarter starter;
  @Autowired private RunCancellationService cancellation;
  @Autowired private RuntimeService runtime;
  @Autowired private TopicRepository topics;
  @Autowired private DigestRunRepository runs;
  @Autowired private OperatorNotificationService notifications;
  @Autowired private NewsDigestProperties properties;

  @Test
  void missedScheduledStartIsMadeUpOnceWithARealRun() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Nachholen " + suffix, model, model);
    // Daily at midnight UTC; the app "starts" one hour later
    Instant midnight = Instant.now().truncatedTo(ChronoUnit.DAYS);
    Clock clock = Clock.fixed(midnight.plus(Duration.ofHours(1)), ZoneOffset.UTC);
    SchedulerTestAccess scheduler =
        new SchedulerTestAccess(
            topics, runs, starter, new SimpleMeterRegistry(), clock, properties);
    try {
      assertThat(scheduler.catchUp(topic, "Nachholen " + suffix, "0 0 0 * * *", "UTC")).isTrue();
      String instance =
          jdbc.queryForObject(
              "SELECT process_instance_id FROM digest_run WHERE topic_id = ?", String.class, topic);
      awaitEnd(instance);
      assertThat(runStatus(instance)).isEqualTo("COMPLETED");

      // A second start of the app does not start the same scheduled run again
      assertThat(scheduler.catchUp(topic, "Nachholen " + suffix, "0 0 0 * * *", "UTC")).isFalse();
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void cancellingARunningRunEndsTheProcessAndFreesTheTopic() {
    String suffix = unique();
    long slow = model("slow-" + suffix);
    long topic = topic("Abbruch " + suffix, model("ok-aggregator-" + suffix), slow);

    String instance = starter.start(topic);
    long runId = runId(instance);
    try {
      fakeProvider.awaitSlowCall();

      cancellation.cancel(runId, "Test-Abbruch");

      assertThat(runStatus(instance)).isEqualTo("CANCELLED");
      assertThat(
              jdbc.queryForObject(
                  "SELECT error_message FROM digest_run WHERE id = ?", String.class, runId))
          .isEqualTo("Test-Abbruch");
      assertThat(runtime.createProcessInstanceQuery().processInstanceId(instance).singleResult())
          .isNull();
      assertThatThrownBy(() -> cancellation.cancel(runId, "noch einmal"))
          .isInstanceOf(RunNotRunningException.class);
    } finally {
      fakeProvider.release();
    }
    // The research job that was still waiting must not change the cancelled run
    sleepQuietly(Duration.ofSeconds(2));
    assertThat(runStatus(instance)).isEqualTo("CANCELLED");

    // The topic can run again
    jdbc.update("DELETE FROM topic_model WHERE topic_id = ?", topic);
    jdbc.update(
        "INSERT INTO topic_model (topic_id, model_id) VALUES (?, ?)", topic, model("ok-" + suffix));
    String next = starter.start(topic);
    awaitEnd(next);
    assertThat(runStatus(next)).isEqualTo("COMPLETED");
  }

  @Test
  void unknownRunCannotBeCancelled() {
    assertThatThrownBy(() -> cancellation.cancel(Long.MAX_VALUE, "x"))
        .isInstanceOf(RunNotFoundException.class);
  }

  @Test
  void runLongerThanTheMaximumDurationIsCancelledAndTheOperatorInformed() {
    String suffix = unique();
    long slow = model("slow-" + suffix);
    String topicName = "Überfällig " + suffix;
    long topic = topic(topicName, model("ok-aggregator-" + suffix), slow);
    String subject = "[News Digest] Hinweis für den Betreiber – " + topicName;
    // The maintenance job runs four hours later
    OverdueRunCanceller overdue =
        new OverdueRunCanceller(
            runs,
            cancellation,
            notifications,
            Clock.offset(Clock.systemUTC(), Duration.ofHours(4)),
            properties);

    String instance = starter.start(topic);
    long runId = runId(instance);
    try {
      fakeProvider.awaitSlowCall();

      assertThat(overdue.cancelOverdue()).contains(runId);
    } finally {
      fakeProvider.release();
    }

    assertThat(runStatus(instance)).isEqualTo("CANCELLED");
    Mailpit.Mail mail = awaitOperatorMail(subject);
    assertThat(mail.text())
        .contains("Höchstdauer überschritten")
        .contains("Automatisch abgebrochen: Höchstdauer von 3 h überschritten");
  }

  private static Mailpit.Mail awaitOperatorMail(String subject) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    while (true) {
      for (Mailpit.Mail mail : Mailpit.mailsTo(PostgresTestSupport.MAIL_OPERATOR)) {
        if (mail.subject().equals(subject)) {
          return mail;
        }
      }
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("No operator mail '" + subject + "'");
      }
      sleepQuietly(Duration.ofMillis(200));
    }
  }

  private static void sleepQuietly(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
