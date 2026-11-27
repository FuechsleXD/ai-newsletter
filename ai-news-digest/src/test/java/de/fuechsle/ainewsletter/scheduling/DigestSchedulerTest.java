package de.fuechsle.ainewsletter.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fuechsle.ainewsletter.process.DigestProcessStarter;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.TopicRepository;
import de.fuechsle.ainewsletter.service.RunAlreadyActiveException;
import de.fuechsle.ainewsletter.support.TestProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

class DigestSchedulerTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
  private static final String MONDAY_7 = "0 0 7 * * MON";
  // Monday 2026-11-23 07:00 in Berlin is 06:00 UTC
  private static final Instant MONDAY_7_BERLIN = Instant.parse("2026-11-23T06:00:00Z");

  private final TopicRepository topics = mock(TopicRepository.class);
  private final DigestRunRepository runs = mock(DigestRunRepository.class);
  private final DigestProcessStarter starter = mock(DigestProcessStarter.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private DigestScheduler scheduler = scheduler(MONDAY_7_BERLIN.plus(Duration.ofHours(2)));

  @AfterEach
  void stopScheduler() {
    scheduler.stop();
  }

  @Test
  void lastFireTimeLiesWithinTheGraceOrIsEmpty() {
    CronExpression cron = CronExpression.parse(MONDAY_7);
    Duration grace = Duration.ofHours(6);

    assertThat(DigestScheduler.lastFireTime(cron, BERLIN, MONDAY_7_BERLIN, grace))
        .contains(MONDAY_7_BERLIN);
    assertThat(
            DigestScheduler.lastFireTime(
                cron, BERLIN, MONDAY_7_BERLIN.plus(Duration.ofHours(6)).minusSeconds(1), grace))
        .contains(MONDAY_7_BERLIN);
    assertThat(
            DigestScheduler.lastFireTime(
                cron, BERLIN, MONDAY_7_BERLIN.plus(Duration.ofHours(6)), grace))
        .isEmpty();
    assertThat(DigestScheduler.lastFireTime(cron, BERLIN, MONDAY_7_BERLIN.minusSeconds(1), grace))
        .isEmpty();
    // The time zone of the schedule counts, not UTC
    assertThat(DigestScheduler.lastFireTime(cron, ZoneOffset.UTC, MONDAY_7_BERLIN, grace))
        .isEmpty();
  }

  @Test
  void missedStartWithinTheGraceIsMadeUpOnce() {
    ScheduledTopic topic = topic(1, MONDAY_7, "Europe/Berlin");
    when(runs.existsByTopicIdAndStartedAtGreaterThanEqual(1L, MONDAY_7_BERLIN)).thenReturn(false);

    assertThat(scheduler.catchUp(topic)).isTrue();
    verify(starter).start(1L);
  }

  @Test
  void startThatAlreadyHappenedIsNotMadeUp() {
    ScheduledTopic topic = topic(1, MONDAY_7, "Europe/Berlin");
    when(runs.existsByTopicIdAndStartedAtGreaterThanEqual(1L, MONDAY_7_BERLIN)).thenReturn(true);

    assertThat(scheduler.catchUp(topic)).isFalse();
    verify(starter, never()).start(anyLong());
  }

  @Test
  void startMissedLongerAgoThanTheGraceIsNotMadeUp() {
    scheduler.stop();
    scheduler = scheduler(MONDAY_7_BERLIN.plus(Duration.ofHours(7)));

    assertThat(scheduler.catchUp(topic(1, MONDAY_7, "Europe/Berlin"))).isFalse();
    verify(starter, never()).start(anyLong());
  }

  @Test
  void invalidScheduleIsNeitherMadeUpNorScheduled() {
    ScheduledTopic badCron = topic(1, "every monday", "Europe/Berlin");
    ScheduledTopic badZone = topic(2, MONDAY_7, "Mars/Olympus");
    ScheduledTopic valid = topic(3, MONDAY_7, "Europe/Berlin");
    when(topics.findScheduled()).thenReturn(List.of(badCron, badZone, valid));

    scheduler.reload();

    assertThat(scheduler.scheduledTopicIds()).containsExactly(3L);
    assertThat(scheduler.catchUp(badCron)).isFalse();
    assertThat(scheduler.catchUp(badZone)).isFalse();
  }

  @Test
  void reloadAddsChangesAndRemovesTopics() {
    when(topics.findScheduled())
        .thenReturn(List.of(topic(1, MONDAY_7, "Europe/Berlin"), topic(2, MONDAY_7, "UTC")));
    scheduler.reload();
    assertThat(scheduler.scheduledTopicIds()).containsExactlyInAnyOrder(1L, 2L);

    // Topic 1 gets an invalid cron expression, topic 2 is deactivated, topic 3 is new
    when(topics.findScheduled())
        .thenReturn(List.of(topic(1, "nonsense", "Europe/Berlin"), topic(3, MONDAY_7, "UTC")));
    scheduler.reload();
    assertThat(scheduler.scheduledTopicIds()).containsExactly(3L);

    // Topic 1 is fixed again
    when(topics.findScheduled())
        .thenReturn(List.of(topic(1, "0 30 6 * * *", "Europe/Berlin"), topic(3, MONDAY_7, "UTC")));
    scheduler.reload();
    assertThat(scheduler.scheduledTopicIds()).containsExactlyInAnyOrder(1L, 3L);
  }

  @Test
  void startWhileARunIsActiveIsSkippedAndCounted() {
    when(starter.start(1L)).thenThrow(new RunAlreadyActiveException(1L));

    assertThat(scheduler.fire(topic(1, MONDAY_7, "Europe/Berlin"))).isFalse();
    assertThat(scheduler.fire(topic(1, MONDAY_7, "Europe/Berlin"))).isFalse();

    assertThat(
            meters
                .get(DigestScheduler.SKIPPED_METRIC)
                .tag("topic", "Topic 1")
                .tag("reason", "already_running")
                .counter()
                .count())
        .isEqualTo(2);
  }

  @Test
  void failingStartDoesNotThrow() {
    when(starter.start(eq(1L))).thenThrow(new IllegalStateException("engine down"));

    assertThat(scheduler.fire(topic(1, MONDAY_7, "Europe/Berlin"))).isFalse();
    verify(runs, never()).existsByTopicIdAndStartedAtGreaterThanEqual(any(), any());
  }

  private DigestScheduler scheduler(Instant now) {
    return new DigestScheduler(
        topics, runs, starter, meters, Clock.fixed(now, ZoneOffset.UTC), TestProperties.defaults());
  }

  private static ScheduledTopic topic(long id, String cron, String zone) {
    return new ScheduledTopic(id, "Topic " + id, cron, zone);
  }
}
