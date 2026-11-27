package de.fuechsle.ainewsletter.aggregate;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Result of the aggregator call including usage and cost of all calls (also when it failed).
 *
 * @param aggregate the digest content, {@code null} if the aggregation failed
 * @param errorMessage reason of the failure (German, shown to admins), {@code null} on success
 */
public record AggregationOutcome(
    DigestAggregate aggregate,
    long inputTokens,
    long outputTokens,
    BigDecimal costUsd,
    Duration duration,
    String errorMessage,
    boolean costEstimated) {

  public boolean succeeded() {
    return aggregate != null;
  }
}
