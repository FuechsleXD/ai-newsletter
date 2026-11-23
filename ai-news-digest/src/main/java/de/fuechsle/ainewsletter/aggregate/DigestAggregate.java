package de.fuechsle.ainewsletter.aggregate;

import java.util.List;

/**
 * Content of a digest (docs/09-pdf-design.md, "Aggregator-Ausgabe"); HTML mail and PDF are rendered
 * from it and it is stored as {@code digest_run.aggregate_json}. All texts come from an LLM and are
 * untrusted (golden rule 3). {@code sources} and {@code models} are not taken from the aggregator
 * but set by the app from the checked news items.
 */
public record DigestAggregate(
    String title,
    String subtitle,
    String summary,
    List<KeyPoint> keyPoints,
    List<Section> sections,
    List<String> takeaways,
    String watchOut,
    List<Source> sources) {

  public DigestAggregate {
    keyPoints = List.copyOf(keyPoints);
    sections = List.copyOf(sections);
    takeaways = List.copyOf(takeaways);
    sources = List.copyOf(sources);
  }

  /** Same content with sources and per-section models set by the app. */
  public DigestAggregate withSourcesAndSections(
      List<Source> newSources, List<Section> newSections) {
    return new DigestAggregate(
        title, subtitle, summary, keyPoints, newSections, takeaways, watchOut, newSources);
  }

  public record KeyPoint(String iconKey, String text) {}

  /**
   * One topic card.
   *
   * @param flowSteps optional short steps of a flow; empty if not used
   * @param highlightNumber optional key figure; {@code null} if not used
   * @param sourceIds IDs of the news items ({@code news_item.id}) the section is based on
   * @param models display names of the models that reported these news items (consensus)
   */
  public record Section(
      String heading,
      String iconKey,
      String text,
      List<String> flowSteps,
      HighlightNumber highlightNumber,
      List<String> sourceIds,
      List<String> models) {

    public Section {
      flowSteps = List.copyOf(flowSteps);
      sourceIds = List.copyOf(sourceIds);
      models = List.copyOf(models);
    }

    public Section withModels(List<String> newModels) {
      return new Section(heading, iconKey, text, flowSteps, highlightNumber, sourceIds, newModels);
    }
  }

  public record HighlightNumber(String value, String label) {}

  /**
   * A checked news item used by the digest.
   *
   * @param id {@code news_item.id}
   */
  public record Source(String id, String title, String url) {}
}
