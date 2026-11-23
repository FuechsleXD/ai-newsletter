package de.fuechsle.ainewsletter.render;

import java.util.List;

/**
 * What the PDF and mail templates show: the aggregate with shortened texts, numbered sections and
 * sources, and the run metadata. All texts are untrusted; the templates escape them (golden rule
 * 3).
 *
 * @param period run period, e.g. {@code 16.11.2026 – 22.11.2026}
 * @param createdOn creation date, e.g. {@code 23.11.2026}
 * @param modelCount number of distinct research models in the consensus
 */
public record DigestView(
    String topicName,
    String title,
    String subtitle,
    String period,
    String createdOn,
    String summary,
    List<KeyPointView> keyPoints,
    List<SectionView> sections,
    List<String> takeaways,
    String watchOut,
    List<SourceView> sources,
    int modelCount) {

  public DigestView {
    keyPoints = List.copyOf(keyPoints);
    sections = List.copyOf(sections);
    takeaways = List.copyOf(takeaways);
    sources = List.copyOf(sources);
  }

  public record KeyPointView(String iconKey, String text) {}

  /**
   * One topic card.
   *
   * @param color accent color of the card ({@code blue}, {@code green}, ...)
   * @param colorHex the same color as hex value, for the inline CSS of the mail
   * @param highlightValue key figure or {@code null}
   * @param sourceNumbers numbers of the sources in {@link DigestView#sources()}
   * @param unconfirmed only one model reported the news items of this section
   */
  public record SectionView(
      int number,
      String color,
      String colorHex,
      String heading,
      String iconKey,
      String text,
      List<String> flowSteps,
      String highlightValue,
      String highlightLabel,
      List<Integer> sourceNumbers,
      List<String> models,
      boolean unconfirmed) {

    public SectionView {
      flowSteps = List.copyOf(flowSteps);
      sourceNumbers = List.copyOf(sourceNumbers);
      models = List.copyOf(models);
    }
  }

  /**
   * A checked source; only these URLs are linked.
   *
   * @param domain host of the URL for the compact source list
   */
  public record SourceView(int number, String title, String url, String domain) {}
}
