package de.fuechsle.ainewsletter.research;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * What one research model should search for.
 *
 * @param topicDescription optional focus of the topic, may be {@code null}
 * @param periodFrom first day of the period (inclusive) in {@code timezone}
 * @param periodTo last day of the period (inclusive) in {@code timezone}
 * @param timezone time zone of the topic's schedule
 */
public record ResearchRequest(
    String topicName,
    String topicDescription,
    LocalDate periodFrom,
    LocalDate periodTo,
    ZoneId timezone,
    ResearchModel model) {

  public ResearchRequest {
    Objects.requireNonNull(topicName, "topicName");
    Objects.requireNonNull(periodFrom, "periodFrom");
    Objects.requireNonNull(periodTo, "periodTo");
    Objects.requireNonNull(timezone, "timezone");
    Objects.requireNonNull(model, "model");
    if (periodFrom.isAfter(periodTo)) {
      throw new IllegalArgumentException("periodFrom must not be after periodTo");
    }
  }
}
