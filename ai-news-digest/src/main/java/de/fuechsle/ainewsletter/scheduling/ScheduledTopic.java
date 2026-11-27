package de.fuechsle.ainewsletter.scheduling;

/** An active topic with the cron expression (Spring format) and time zone of its schedule. */
public record ScheduledTopic(
    long topicId, String topicName, String cronExpression, String timezone) {}
