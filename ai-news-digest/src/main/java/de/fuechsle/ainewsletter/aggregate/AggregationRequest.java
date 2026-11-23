package de.fuechsle.ainewsletter.aggregate;

import de.fuechsle.ainewsletter.provider.ModelSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * What the aggregator should condense.
 *
 * @param topicDescription optional focus of the topic, may be {@code null}
 * @param items checked news items with status {@code VALID}
 */
public record AggregationRequest(
    String topicName,
    String topicDescription,
    LocalDate periodFrom,
    LocalDate periodTo,
    ModelSnapshot model,
    List<AggregationItem> items) {

  public AggregationRequest {
    Objects.requireNonNull(topicName, "topicName");
    Objects.requireNonNull(periodFrom, "periodFrom");
    Objects.requireNonNull(periodTo, "periodTo");
    Objects.requireNonNull(model, "model");
    items = List.copyOf(items);
  }
}
