package de.fuechsle.ainewsletter.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate.HighlightNumber;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.KeyPoint;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Section;
import de.fuechsle.ainewsletter.support.Fixtures;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AggregateResponseParserTest {

  private static final Set<String> KNOWN = Set.of("1", "3");

  private final AggregateResponseParser parser =
      new AggregateResponseParser(new JsonMapper(), new IconKeys());

  @Test
  void realAnswerIsParsedWithoutCorrections() {
    // Aggregate of Claude Sonnet 5.5 for aggregate prompt v2 (eval run 4, docs/13-roadmap.md 9b),
    // without the sources and models the app adds afterwards
    DigestAggregate aggregate =
        parser.parse(
            Fixtures.read("aggregate/real-claude-v2.json"), Set.of("7", "8", "9", "10"), 6);

    assertThat(aggregate.title()).isEqualTo("KI-Briefing: GPT-6.2, Gemini 4 Neon, US-Regulierung");
    assertThat(aggregate.sections())
        .extracting(Section::sourceIds)
        .containsExactly(List.of("7", "9"), List.of("8"), List.of("10"));
    // Only allowed icon keys were used, nothing had to be replaced by the default
    assertThat(aggregate.sections()).extracting(Section::iconKey).doesNotContain(IconKeys.DEFAULT);
    assertThat(aggregate.keyPoints()).isNotEmpty();
    assertThat(aggregate.watchOut()).isNotBlank();
  }

  @Test
  void validAnswerIsParsedAndCorrected() {
    DigestAggregate aggregate = parser.parse(Fixtures.read("aggregate/valid.json"), KNOWN, 6);

    assertThat(aggregate.title()).isEqualTo("Java im Überblick");
    assertThat(aggregate.subtitle()).isEqualTo("16. bis 22. November 2026");
    assertThat(aggregate.keyPoints())
        .containsExactly(
            new KeyPoint("code", "Spring Boot 4.1.3 ist da"),
            new KeyPoint("rocket", "Neue Java-Preview"),
            new KeyPoint(IconKeys.DEFAULT, "Unbekanntes Icon"));
    assertThat(aggregate.takeaways()).containsExactly("Upgrade planen", "Preview testen");
    assertThat(aggregate.watchOut()).isEqualTo("Auf das nächste Release achten.");
    assertThat(aggregate.sources()).isEmpty();

    Section first = aggregate.sections().getFirst();
    // Unknown source ID "2" removed, models are never taken from the aggregator
    assertThat(first.sourceIds()).containsExactly("1");
    assertThat(first.models()).isEmpty();
    assertThat(first.flowSteps()).containsExactly("Upgrade", "Testen", "Ausrollen");
    assertThat(first.highlightNumber()).isNull();

    Section second = aggregate.sections().get(1);
    // Numeric source IDs are accepted; highlight number wins over flow steps
    assertThat(second.sourceIds()).containsExactly("3");
    assertThat(second.highlightNumber()).isEqualTo(new HighlightNumber("2", "Preview-Features"));
    assertThat(second.flowSteps()).isEmpty();
  }

  @Test
  void textAroundTheJsonIsIgnoredAndOptionalFieldsAreEmpty() {
    DigestAggregate aggregate =
        parser.parse(Fixtures.read("aggregate/with-preamble-and-fence.txt"), KNOWN, 6);

    assertThat(aggregate.title()).isEqualTo("Kurz");
    assertThat(aggregate.subtitle()).isEmpty();
    assertThat(aggregate.watchOut()).isEmpty();
    assertThat(aggregate.keyPoints()).isEmpty();
    assertThat(aggregate.takeaways()).isEmpty();
    assertThat(aggregate.sections()).hasSize(1);
  }

  @Test
  void sectionsAboveTheMaximumAndSectionsWithoutHeadingOrTextAreDropped() {
    DigestAggregate aggregate =
        parser.parse(Fixtures.read("aggregate/too-many-sections.json"), KNOWN, 6);

    assertThat(aggregate.sections())
        .extracting(Section::heading)
        .containsExactly("Thema 1", "Thema 2", "Thema 3", "Thema 4", "Thema 5", "Thema 6");
  }

  @Test
  void answerWithoutAnySectionWithKnownSourcesIsInvalid() {
    assertThatThrownBy(
            () -> parser.parse(Fixtures.read("aggregate/unknown-sources.json"), KNOWN, 6))
        .isInstanceOf(AggregateParseException.class)
        .hasMessageContaining("No section");
  }

  @Test
  void brokenJsonIsInvalid() {
    assertThatThrownBy(() -> parser.parse(Fixtures.read("aggregate/invalid-json.txt"), KNOWN, 6))
        .isInstanceOf(AggregateParseException.class)
        .hasMessageStartingWith("Invalid JSON");
  }

  @Test
  void missingTitleOrSummaryIsInvalid() {
    assertThatThrownBy(() -> parser.parse("{\"summary\": \"s\", \"sections\": []}", KNOWN, 6))
        .isInstanceOf(AggregateParseException.class)
        .hasMessageContaining("\"title\"");
    assertThatThrownBy(() -> parser.parse("{\"title\": \"t\", \"summary\": \" \"}", KNOWN, 6))
        .isInstanceOf(AggregateParseException.class)
        .hasMessageContaining("\"summary\"");
  }

  @Test
  void answerWithoutJsonIsInvalid() {
    assertThatThrownBy(() -> parser.parse("Kein JSON.", KNOWN, 6))
        .isInstanceOf(AggregateParseException.class);
    assertThatThrownBy(() -> parser.parse(null, KNOWN, 6))
        .isInstanceOf(AggregateParseException.class);
  }

  @Test
  void incompleteHighlightNumberAndWrongTypesAreIgnored() {
    String answer =
        """
        {"title": "t", "summary": "s", "keyPoints": "kein Array",
         "sections": [{"heading": "h", "text": "x", "sourceIds": ["1", true, "1"],
                       "highlightNumber": {"value": "5"}, "flowSteps": ["a", 3]}]}
        """;

    Section section = parser.parse(answer, KNOWN, 6).sections().getFirst();

    assertThat(section.sourceIds()).containsExactly("1");
    assertThat(section.highlightNumber()).isNull();
    assertThat(section.flowSteps()).containsExactly("a");
    assertThat(section.iconKey()).isEqualTo(IconKeys.DEFAULT);
  }
}
