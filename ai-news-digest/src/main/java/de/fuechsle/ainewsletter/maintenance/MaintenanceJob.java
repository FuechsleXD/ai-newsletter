package de.fuechsle.ainewsletter.maintenance;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Runs the maintenance tasks {@code newsdigest.maintenance.initial-delay} after the start of the
 * app and then every {@code newsdigest.maintenance.interval} (ADR-019): cancel overdue runs, apply
 * the retention periods, start the history cleanup of the engine. Not at a fixed time of day,
 * because the instance may only run for a short time. Every task is idempotent; one failing task
 * does not stop the others.
 */
@Component
@ConditionalOnProperty(
    name = "newsdigest.maintenance.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MaintenanceJob {

  private static final Logger log = LoggerFactory.getLogger(MaintenanceJob.class);

  private final OverdueRunCanceller overdue;
  private final RetentionService retention;
  private final Clock clock;
  private final Duration initialDelay;
  private final Duration interval;
  private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

  public MaintenanceJob(
      OverdueRunCanceller overdue,
      RetentionService retention,
      Clock clock,
      NewsDigestProperties properties) {
    this.overdue = overdue;
    this.retention = retention;
    this.clock = clock;
    this.initialDelay = properties.maintenance().initialDelay();
    this.interval = properties.maintenance().interval();
    taskScheduler.setPoolSize(1);
    taskScheduler.setThreadNamePrefix("maintenance-");
    taskScheduler.initialize();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    taskScheduler.scheduleWithFixedDelay(this::run, clock.instant().plus(initialDelay), interval);
  }

  @PreDestroy
  public void stop() {
    taskScheduler.shutdown();
  }

  /** One maintenance pass. */
  public void run() {
    attempt("cancel overdue runs", overdue::cancelOverdue);
    attempt("apply retention periods", retention::cleanUp);
    attempt("start history cleanup", retention::startHistoryCleanup);
  }

  private static void attempt(String task, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.error("Maintenance task '{}' failed", task, e);
    }
  }
}
