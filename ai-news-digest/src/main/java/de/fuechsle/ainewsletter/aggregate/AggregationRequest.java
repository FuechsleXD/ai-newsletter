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
 * @param previousHeadlines section headings of the last completed run of the topic, so the
 *     aggregator does not repeat them; empty if there is none
 */
public record AggregationRequest(
    String topicName,
    String topicDescription,
    LocalDate periodFrom,
    LocalDate periodTo,
    ModelSnapshot model,
    List<AggregationItem> items,
    List<String> previousHeadlines) {

  public AggregationRequest(
      String topicName,
      String topicDescription,
      LocalDate periodFrom,
      LocalDate periodTo,
      ModelSnapshot model,
      List<AggregationItem> items) {
    this(topicName, topicDescription, periodFrom, periodTo, model, items, List.of());
  }

  public AggregationRequest {
    Objects.requireNonNull(topicName, "topicName");
    Objects.requireNonNull(periodFrom, "periodFrom");
    Objects.requireNonNull(periodTo, "periodTo");
    Objects.requireNonNull(model, "model");
    items = List.copyOf(items);
    previousHeadlines = previousHeadlines == null ? List.of() : List.copyOf(previousHeadlines);
  }
}
