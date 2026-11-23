package de.fuechsle.ainewsletter.render;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Section;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Source;
import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.render.DigestView.KeyPointView;
import de.fuechsle.ainewsletter.render.DigestView.SectionView;
import de.fuechsle.ainewsletter.render.DigestView.SourceView;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link DigestView} from an aggregate: shortens all texts to the length limits
 * (headings and short lines to {@code max-heading-chars}, texts to {@code max-text-chars}), numbers
 * the sections and sources and marks sections reported by only one model as unconfirmed.
 */
@Component
public class DigestViewFactory {

  static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

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

  private final int headingChars;
  private final int textChars;

  public DigestViewFactory(NewsDigestProperties properties) {
    this.headingChars = properties.output().maxHeadingChars();
    this.textChars = properties.output().maxTextChars();
  }

  public DigestView create(
      DigestAggregate aggregate,
      String topicName,
      LocalDate periodFrom,
      LocalDate periodTo,
      LocalDate createdOn) {
    List<SourceView> sources = new ArrayList<>();
    Map<String, Integer> sourceNumbers = new HashMap<>();
    for (Source source : aggregate.sources()) {
      int number = sources.size() + 1;
      sourceNumbers.put(source.id(), number);
      sources.add(
          new SourceView(number, heading(source.title()), source.url(), domain(source.url())));
    }

    List<SectionView> sections = new ArrayList<>();
    for (Section section : aggregate.sections()) {
      sections.add(section(section, sections.size(), sourceNumbers));
    }

    return new DigestView(
        heading(topicName),
        heading(aggregate.title()),
        heading(aggregate.subtitle()),
        DATE.format(periodFrom) + " – " + DATE.format(periodTo),
        DATE.format(createdOn),
        text(aggregate.summary()),
        aggregate.keyPoints().stream()
            .map(point -> new KeyPointView(point.iconKey(), heading(point.text())))
            .toList(),
        sections,
        aggregate.takeaways().stream().map(this::text).toList(),
        text(aggregate.watchOut()),
        sources,
        (int)
            aggregate.sections().stream()
                .flatMap(section -> section.models().stream())
                .distinct()
                .count());
  }

  private SectionView section(Section section, int index, Map<String, Integer> sourceNumbers) {
    DigestAggregate.HighlightNumber highlight = section.highlightNumber();
    String color = COLORS.get(index % COLORS.size());
    return new SectionView(
        index + 1,
        color,
        COLOR_HEX.get(color),
        heading(section.heading()),
        section.iconKey(),
        text(section.text()),
        section.flowSteps().stream().map(this::heading).toList(),
        highlight == null ? null : heading(highlight.value()),
        highlight == null ? null : heading(highlight.label()),
        section.sourceIds().stream().map(sourceNumbers::get).filter(Objects::nonNull).toList(),
        section.models(),
        section.models().size() < 2);
  }

  private String heading(String value) {
    return TextShortener.shorten(value, headingChars);
  }

  private String text(String value) {
    return TextShortener.shorten(value, textChars);
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
