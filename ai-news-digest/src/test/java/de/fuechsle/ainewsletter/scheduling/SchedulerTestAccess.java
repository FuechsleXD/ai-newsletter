package de.fuechsle.ainewsletter.scheduling;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.process.DigestProcessStarter;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.TopicRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;

/**
 * A {@link DigestScheduler} for tests in other packages. It is never started: tests call the
 * catch-up of a single topic, so the topics of other tests in the shared database stay untouched.
 */
public class SchedulerTestAccess {

  private final DigestScheduler scheduler;

  public SchedulerTestAccess(
      TopicRepository topics,
      DigestRunRepository runs,
      DigestProcessStarter starter,
      MeterRegistry meters,
      Clock clock,
      NewsDigestProperties properties) {
    scheduler = new DigestScheduler(topics, runs, starter, meters, clock, properties);
  }

  public boolean catchUp(long topicId, String topicName, String cron, String timezone) {
    return scheduler.catchUp(new ScheduledTopic(topicId, topicName, cron, timezone));
  }

  public void stop() {
    scheduler.stop();
  }
}
