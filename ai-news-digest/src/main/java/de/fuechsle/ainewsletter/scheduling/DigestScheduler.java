package de.fuechsle.ainewsletter.scheduling;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.process.DigestProcessStarter;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.TopicRepository;
import de.fuechsle.ainewsletter.service.RunAlreadyActiveException;
import de.fuechsle.ainewsletter.service.TopicNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Starts digest runs on the schedules from the database (ADR-019). Topics and schedules are
 * reloaded every {@code newsdigest.run.config-reload-interval}; a changed cron expression or time
 * zone takes effect without a restart. On start of the app, a scheduled start missed within {@code
 * newsdigest.scheduler.misfire-grace} is made up once, so a run is not lost when the instance
 * starts late.
 *
 * <p>Only active with {@code newsdigest.scheduler.enabled=true} (profile {@code prod}). The budget
 * is checked by the process itself; a start while a run of the topic is still active is skipped,
 * logged and counted ({@code digest.run.skipped}).
 */
@Component
@ConditionalOnProperty(name = "newsdigest.scheduler.enabled", havingValue = "true")
public class DigestScheduler {

  static final String SKIPPED_METRIC = "digest.run.skipped";

  private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

  private final TopicRepository topics;
  private final DigestRunRepository runs;
  private final DigestProcessStarter starter;
  private final MeterRegistry meters;
  private final Clock clock;
  private final Duration reloadInterval;
  private final Duration misfireGrace;
  private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
  private final Map<Long, Registration> registrations = new ConcurrentHashMap<>();

  public DigestScheduler(
      TopicRepository topics,
      DigestRunRepository runs,
      DigestProcessStarter starter,
      MeterRegistry meters,
      Clock clock,
      NewsDigestProperties properties) {
    this.topics = topics;
    this.runs = runs;
    this.starter = starter;
    this.meters = meters;
    this.clock = clock;
    this.reloadInterval = properties.run().configReloadInterval();
    this.misfireGrace = properties.scheduler().misfireGrace();
    taskScheduler.setPoolSize(1);
    taskScheduler.setThreadNamePrefix("digest-scheduler-");
    taskScheduler.setClock(clock);
    taskScheduler.initialize();
  }

  /** Makes up missed starts, then loads the schedules and keeps reloading them. */
  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    catchUpMissedStarts();
    taskScheduler.scheduleWithFixedDelay(this::reloadSafely, reloadInterval);
  }

  @PreDestroy
  public void stop() {
    taskScheduler.shutdown();
  }

  /** Brings the registered triggers in line with the active topics and their schedules. */
  synchronized void reload() {
    List<ScheduledTopic> current = topics.findScheduled();
    Set<Long> active = new HashSet<>();
    for (ScheduledTopic topic : current) {
      active.add(topic.topicId());
      Registration existing = registrations.get(topic.topicId());
      if (existing != null && existing.topic().equals(topic)) {
        continue;
      }
      if (existing != null) {
        existing.cancel();
      }
      registrations.put(topic.topicId(), register(topic));
    }
    registrations
        .entrySet()
        .removeIf(entry -> {
          if (active.contains(entry.getKey())) {
            return false;
          }
          entry.getValue().cancel();
          log.info("Topic {} is no longer scheduled", entry.getKey());
          return true;
        });
  }

  /** Topic IDs with a valid trigger (for tests). */
  Set<Long> scheduledTopicIds() {
    Set<Long> ids = new HashSet<>();
    registrations.forEach((id, registration) -> {
      if (registration.future() != null) {
        ids.add(id);
      }
    });
    return ids;
  }

  /** Makes up the missed start of every active topic (see {@link #catchUp(ScheduledTopic)}). */
  void catchUpMissedStarts() {
    for (ScheduledTopic topic : topics.findScheduled()) {
      catchUp(topic);
    }
  }

  /**
   * Starts a run if the last scheduled time of the topic lies within the misfire grace and no run
   * of the topic has started since then.
   *
   * @return whether a run was started
   */
  boolean catchUp(ScheduledTopic topic) {
    Optional<Instant> missed;
    try {
      missed =
          lastFireTime(
              CronExpression.parse(topic.cronExpression()),
              ZoneId.of(topic.timezone()),
              clock.instant(),
              misfireGrace);
    } catch (IllegalArgumentException | DateTimeException e) {
      return false; // logged when the trigger is registered
    }
    if (missed.isEmpty()
        || runs.existsByTopicIdAndStartedAtGreaterThanEqual(topic.topicId(), missed.get())) {
      return false;
    }
    log.info("Making up the missed scheduled start {} of topic {}", missed.get(), topic.topicId());
    return fire(topic);
  }

  /** Last time of the cron expression in {@code (now - grace, now]}, if any. */
  static Optional<Instant> lastFireTime(
      CronExpression cron, ZoneId zone, Instant now, Duration grace) {
    ZonedDateTime end = now.atZone(zone);
    ZonedDateTime last = null;
    for (ZonedDateTime next = cron.next(now.minus(grace).atZone(zone));
        next != null && !next.isAfter(end);
        next = cron.next(next)) {
      last = next;
    }
    return Optional.ofNullable(last).map(ZonedDateTime::toInstant);
  }

  /**
   * Starts a run of the topic; never throws, so a failed start does not end the trigger.
   *
   * @return whether a run was started
   */
  boolean fire(ScheduledTopic topic) {
    try {
      starter.start(topic.topicId());
      return true;
    } catch (RunAlreadyActiveException e) {
      log.warn("Scheduled start of topic {} skipped: a run is still active", topic.topicId());
      Counter.builder(SKIPPED_METRIC)
          .description("Scheduled starts skipped because a run of the topic was still active")
          .tag("topic", topic.topicName())
          .tag("reason", "already_running")
          .register(meters)
          .increment();
    } catch (TopicNotFoundException e) {
      log.info("Scheduled start of topic {} skipped: topic was deleted", topic.topicId());
    } catch (RuntimeException e) {
      log.error("Scheduled start of topic {} failed", topic.topicId(), e);
    }
    return false;
  }

  private Registration register(ScheduledTopic topic) {
    try {
      CronTrigger trigger = new CronTrigger(topic.cronExpression(), ZoneId.of(topic.timezone()));
      ScheduledFuture<?> future = taskScheduler.schedule(() -> fire(topic), trigger);
      log.info(
          "Topic {} scheduled with '{}' ({})",
          topic.topicId(),
          topic.cronExpression(),
          topic.timezone());
      return new Registration(topic, future);
    } catch (IllegalArgumentException | DateTimeException e) {
      log.error(
          "Schedule of topic {} is invalid ('{}', {}), the topic is not started on schedule: {}",
          topic.topicId(),
          topic.cronExpression(),
          topic.timezone(),
          e.getMessage());
      return new Registration(topic, null);
    }
  }

  private void reloadSafely() {
    try {
      reload();
    } catch (RuntimeException e) {
      log.error("Reloading the schedules failed, keeping the previous ones", e);
    }
  }

  /** A registered topic; {@code future} is null if its schedule is invalid. */
  private record Registration(ScheduledTopic topic, ScheduledFuture<?> future) {

    void cancel() {
      if (future != null) {
        future.cancel(false);
      }
    }
  }
}
