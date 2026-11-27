package de.fuechsle.ainewsletter.maintenance;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.RunStatus;
import de.fuechsle.ainewsletter.mail.OperatorNotificationReason;
import de.fuechsle.ainewsletter.mail.OperatorNotificationService;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.service.RunCancellationService;
import de.fuechsle.ainewsletter.service.RunNotRunningException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cancels runs that are still {@code RUNNING} after {@code newsdigest.run.max-duration} and informs
 * the operator. With the instance only running on schedule, nobody would notice a run stuck on an
 * incident, and it would block every later run of the topic.
 */
@Service
public class OverdueRunCanceller {

  private static final Logger log = LoggerFactory.getLogger(OverdueRunCanceller.class);

  private final DigestRunRepository runs;
  private final RunCancellationService cancellation;
  private final OperatorNotificationService notifications;
  private final Clock clock;
  private final Duration maxDuration;

  public OverdueRunCanceller(
      DigestRunRepository runs,
      RunCancellationService cancellation,
      OperatorNotificationService notifications,
      Clock clock,
      NewsDigestProperties properties) {
    this.runs = runs;
    this.cancellation = cancellation;
    this.notifications = notifications;
    this.clock = clock;
    this.maxDuration = properties.run().maxDuration();
  }

  /**
   * Cancels every overdue run, each in its own transaction; a failed operator mail is only logged.
   *
   * @return IDs of the cancelled runs
   */
  public List<Long> cancelOverdue() {
    List<DigestRun> overdue =
        runs.findByStatusAndStartedAtBeforeOrderByStartedAt(
            RunStatus.RUNNING, clock.instant().minus(maxDuration));
    return overdue.stream().map(DigestRun::getId).filter(this::cancel).toList();
  }

  private boolean cancel(long runId) {
    try {
      cancellation.cancel(runId, reason(maxDuration));
    } catch (RunNotRunningException e) {
      return false; // ended in the meantime
    }
    try {
      notifications.notify(runId, OperatorNotificationReason.RUN_TIMED_OUT);
    } catch (RuntimeException e) {
      log.error("Run {} was cancelled, but the operator mail failed", runId, e);
    }
    return true;
  }

  static String reason(Duration maxDuration) {
    String limit =
        maxDuration.toMinutesPart() == 0 && maxDuration.toHours() > 0
            ? maxDuration.toHours() + " h"
            : maxDuration.toMinutes() + " min";
    return "Automatisch abgebrochen: Höchstdauer von " + limit + " überschritten";
  }
}
