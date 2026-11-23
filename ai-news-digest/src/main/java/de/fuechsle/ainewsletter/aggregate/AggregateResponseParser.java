package de.fuechsle.ainewsletter.aggregate;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate.HighlightNumber;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.KeyPoint;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Section;
import de.fuechsle.ainewsletter.provider.AnswerParseException;
import de.fuechsle.ainewsletter.provider.JsonAnswer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses and validates the aggregator answer into a {@link DigestAggregate} (docs/09-pdf-design.md,
 * "Aggregator-Ausgabe").
 *
 * <p>Hard violations raise an {@link AggregateParseException} and lead to a repair attempt: no JSON
 * object, title or summary missing, or no usable section left. Soft violations are corrected:
 *
 * <ul>
 *   <li>source IDs that are not among the checked news items are removed; a section without any
 *       known source is dropped, because its content would be invented
 *   <li>sections without heading or text are dropped; sections above the maximum are cut off
 *   <li>unknown icon keys become {@link IconKeys#DEFAULT}
 *   <li>a section with flow steps and a highlight number keeps only the highlight number
 *   <li>fewer sections than the minimum are allowed (few news items do not make more topics)
 * </ul>
 *
 * Texts are not shortened here; that happens before rendering. {@code sources} and {@code models}
 * stay empty and are set by the {@link AggregationService}.
 */
@Component
public class AggregateResponseParser {

  private static final Logger log = LoggerFactory.getLogger(AggregateResponseParser.class);

  private final ObjectMapper objectMapper;
  private final IconKeys iconKeys;

  public AggregateResponseParser(ObjectMapper objectMapper, IconKeys iconKeys) {
    this.objectMapper = objectMapper;
    this.iconKeys = iconKeys;
  }

  /**
   * @param knownSourceIds IDs of the news items that were given to the aggregator
   * @param maxSections maximum number of sections; further sections are cut off
   */
  public DigestAggregate parse(String text, Set<String> knownSourceIds, int maxSections) {
    JsonNode root;
    try {
      root = JsonAnswer.readObject(objectMapper, text);
    } catch (AnswerParseException e) {
      throw new AggregateParseException(e.getMessage(), e);
    }

    String title = required(root, "title");
    String summary = required(root, "summary");
    List<Section> sections = sections(root.get("sections"), knownSourceIds, maxSections);
    if (sections.isEmpty()) {
      throw new AggregateParseException(
          "No section with heading, text and at least one known source ID");
    }
    return new DigestAggregate(
        title,
        optional(root, "subtitle"),
        summary,
        keyPoints(root.get("keyPoints")),
        sections,
        strings(root.get("takeaways")),
        optional(root, "watchOut"),
        List.of());
  }

  private List<Section> sections(JsonNode node, Set<String> knownSourceIds, int maxSections) {
    List<Section> sections = new ArrayList<>();
    int dropped = 0;
    for (JsonNode item : array(node)) {
      Section section = section(item, knownSourceIds);
      if (section == null || sections.size() >= maxSections) {
        dropped++;
      } else {
        sections.add(section);
      }
    }
    if (dropped > 0) {
      log.info("Aggregator answer: {} section(s) dropped or cut off", dropped);
    }
    return sections;
  }

  private Section section(JsonNode item, Set<String> knownSourceIds) {
    String heading = JsonAnswer.text(item, "heading");
    String text = JsonAnswer.text(item, "text");
    List<String> sourceIds =
        ids(item.get("sourceIds")).stream().filter(knownSourceIds::contains).distinct().toList();
    if (heading == null || text == null || sourceIds.isEmpty()) {
      return null;
    }
    HighlightNumber highlight = highlight(item.get("highlightNumber"));
    List<String> flowSteps = highlight != null ? List.of() : strings(item.get("flowSteps"));
    return new Section(
        heading,
        iconKeys.orDefault(JsonAnswer.text(item, "iconKey")),
        text,
        flowSteps,
        highlight,
        sourceIds,
        List.of());
  }

  private List<KeyPoint> keyPoints(JsonNode node) {
    List<KeyPoint> keyPoints = new ArrayList<>();
    for (JsonNode item : array(node)) {
      String text = JsonAnswer.text(item, "text");
      if (text != null) {
        keyPoints.add(new KeyPoint(iconKeys.orDefault(JsonAnswer.text(item, "iconKey")), text));
      }
    }
    return keyPoints;
  }

  private static HighlightNumber highlight(JsonNode node) {
    if (node == null || !node.isObject()) {
      return null;
    }
    String value = JsonAnswer.text(node, "value");
    String label = JsonAnswer.text(node, "label");
    return value == null || label == null ? null : new HighlightNumber(value, label);
  }

  /** Non-blank strings of an array; other elements are ignored. */
  private static List<String> strings(JsonNode node) {
    List<String> values = new ArrayList<>();
    for (JsonNode item : array(node)) {
      if (item.isString() && !item.asString().isBlank()) {
        values.add(item.asString().strip());
      }
    }
    return values;
  }

  /** IDs may come as strings or numbers. */
  private static List<String> ids(JsonNode node) {
    List<String> ids = new ArrayList<>();
    for (JsonNode item : array(node)) {
      if (item.isIntegralNumber()) {
        ids.add(item.asString());
      } else if (item.isString() && !item.asString().isBlank()) {
        ids.add(item.asString().strip());
      }
    }
    return ids;
  }

  private static Iterable<JsonNode> array(JsonNode node) {
    return node != null && node.isArray() ? node : List.of();
  }

  private static String required(JsonNode root, String field) {
    String value = JsonAnswer.text(root, field);
    if (value == null) {
      throw new AggregateParseException("Field \"" + field + "\" is missing or empty");
    }
    return value;
  }

  private static String optional(JsonNode root, String field) {
    String value = JsonAnswer.text(root, field);
    return value == null ? "" : value;
  }
}
