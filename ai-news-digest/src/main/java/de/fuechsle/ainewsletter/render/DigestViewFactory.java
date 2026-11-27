package de.fuechsle.ainewsletter.render;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Section;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Source;
import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.render.DigestView.KeyPointView;
import de.fuechsle.ainewsletter.render.DigestView.ModelBadge;
import de.fuechsle.ainewsletter.render.DigestView.SectionView;
import de.fuechsle.ainewsletter.render.DigestView.SourceView;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link DigestView} from an aggregate: shortens all texts to the length limits
 * (headings and short lines to {@code max-heading-chars}, texts to {@code max-text-chars}, both
 * scaled down for a second rendering attempt), numbers the sections and sources, gives every
 * research model a dot color and marks sections reported by only one model as unconfirmed.
 */
@Component
public class DigestViewFactory {

  /** Accent colors of the topic cards in this order (docs/09-pdf-design.md). */
  static final List<String> COLORS = List.of("blue", "green", "purple", "orange", "red", "teal");

  static final Map<String, String> COLOR_HEX =
      Map.of(
          "blue", "#3b82f6",
          "green", "#22c55e",
          "purple", "#a855f7",
          "orange", "#f97316",
          "red", "#ef4444",
          "teal", "#14b8a6");

  /** Pastel backgrounds of the colors (same as in the PDF template). */
  static final Map<String, String> LIGHT_HEX =
      Map.of(
          "blue", "#eff6ff",
          "green", "#f0fdf4",
          "purple", "#faf5ff",
          "orange", "#fff7ed",
          "red", "#fef2f2",
          "teal", "#f0fdfa");

  /** Dark shades of the colors for headings (same as in the PDF template). */
  static final Map<String, String> DARK_HEX =
      Map.of(
          "blue", "#1d4ed8",
          "green", "#15803d",
          "purple", "#7e22ce",
          "orange", "#c2410c",
          "red", "#b91c1c",
          "teal", "#0f766e");

  /** Dot colors of the research models in the consensus bar, in order of appearance. */
  static final List<String> MODEL_COLORS =
      List.of("#2563eb", "#16a34a", "#9333ea", "#ea580c", "#db2777", "#0d9488");

  /** Lower bounds of the scaled limits, so headings and texts stay readable. */
  static final int MIN_HEADING_CHARS = 20;

  static final int MIN_TEXT_CHARS = 60;

  private final int headingChars;
  private final int textChars;
  private final DigestLanguage language;

  public DigestViewFactory(NewsDigestProperties properties, DigestLanguage language) {
    this.headingChars = properties.output().maxHeadingChars();
    this.textChars = properties.output().maxTextChars();
    this.language = language;
  }

  public DigestView create(
      DigestAggregate aggregate,
      String topicName,
      LocalDate periodFrom,
      LocalDate periodTo,
      LocalDate createdOn) {
    return create(aggregate, topicName, periodFrom, periodTo, createdOn, 1.0);
  }

  /**
   * @param lengthFactor scales the length limits ({@code 1.0} = configured limits), for a second
   *     rendering attempt when the PDF has more than one page
   */
  public DigestView create(
      DigestAggregate aggregate,
      String topicName,
      LocalDate periodFrom,
      LocalDate periodTo,
      LocalDate createdOn,
      double lengthFactor) {
    Limits limits =
        new Limits(
            Math.max(MIN_HEADING_CHARS, (int) Math.round(headingChars * lengthFactor)),
            Math.max(MIN_TEXT_CHARS, (int) Math.round(textChars * lengthFactor)));
    Map<String, String> modelColors = new LinkedHashMap<>();
    for (Section section : aggregate.sections()) {
      for (String model : section.models()) {
        if (!modelColors.containsKey(model)) {
          modelColors.put(model, MODEL_COLORS.get(modelColors.size() % MODEL_COLORS.size()));
        }
      }
    }
    List<SourceView> sources = new ArrayList<>();
    Map<String, Integer> sourceNumbers = new HashMap<>();
    for (Source source : aggregate.sources()) {
      int number = sources.size() + 1;
      sourceNumbers.put(source.id(), number);
      sources.add(
          new SourceView(
              number, limits.heading(source.title()), source.url(), domain(source.url())));
    }

    List<SectionView> sections = new ArrayList<>();
    for (Section section : aggregate.sections()) {
      sections.add(section(section, sections.size(), sourceNumbers, modelColors, limits));
    }

    return new DigestView(
        limits.heading(topicName),
        limits.heading(aggregate.title()),
        limits.heading(aggregate.subtitle()),
        language.date(periodFrom) + " – " + language.date(periodTo),
        language.date(createdOn),
        limits.text(aggregate.summary()),
        aggregate.keyPoints().stream()
            .map(point -> new KeyPointView(point.iconKey(), limits.heading(point.text())))
            .toList(),
        sections,
        aggregate.takeaways().stream().map(limits::text).toList(),
        limits.text(aggregate.watchOut()),
        sources,
        modelColors.size(),
        badges(modelColors.keySet(), modelColors));
  }

  private SectionView section(
      Section section,
      int index,
      Map<String, Integer> sourceNumbers,
      Map<String, String> modelColors,
      Limits limits) {
    DigestAggregate.HighlightNumber highlight = section.highlightNumber();
    String color = COLORS.get(index % COLORS.size());
    return new SectionView(
        index + 1,
        color,
        COLOR_HEX.get(color),
        LIGHT_HEX.get(color),
        DARK_HEX.get(color),
        limits.heading(section.heading()),
        section.iconKey(),
        limits.text(section.text()),
        section.flowSteps().stream().map(limits::heading).toList(),
        highlight == null ? null : limits.heading(highlight.value()),
        highlight == null ? null : limits.heading(highlight.label()),
        section.sourceIds().stream().map(sourceNumbers::get).filter(Objects::nonNull).toList(),
        section.models(),
        badges(section.models(), modelColors),
        section.models().size() < 2);
  }

  private static List<ModelBadge> badges(
      Collection<String> models, Map<String, String> modelColors) {
    return models.stream().map(model -> new ModelBadge(model, modelColors.get(model))).toList();
  }

  /** Length limits of one rendering attempt. */
  private record Limits(int headingChars, int textChars) {

    String heading(String value) {
      return TextShortener.shorten(value, headingChars);
    }

    String text(String value) {
      return TextShortener.shorten(value, textChars);
    }
  }

  /** Host without {@code www.}; the whole URL if it has no host. */
  static String domain(String url) {
    try {
      String host = URI.create(url).getHost();
      if (host == null) {
        return url;
      }
      return host.startsWith("www.") ? host.substring(4) : host;
    } catch (IllegalArgumentException e) {
      return url;
    }
  }
}
