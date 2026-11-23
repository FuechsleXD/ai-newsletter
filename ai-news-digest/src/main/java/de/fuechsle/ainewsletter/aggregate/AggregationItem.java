package de.fuechsle.ainewsletter.aggregate;

import java.util.List;

/**
 * A checked news item as given to the aggregator (placeholder {@code newsItemsJson}).
 *
 * @param id {@code news_item.id}, referenced by the aggregator in {@code sourceIds}
 * @param publishedAt publication date {@code YYYY-MM-DD} or {@code null} if unknown
 * @param models display names of all models that reported this news item in the run (consensus)
 */
public record AggregationItem(
    String id,
    String headline,
    String summary,
    String url,
    String publishedAt,
    List<String> models) {

  public AggregationItem {
    models = List.copyOf(models);
  }
}
