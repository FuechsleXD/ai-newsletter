package de.fuechsle.ainewsletter.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.support.Fixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ResearchResponseParserTest {

  private final ResearchResponseParser parser = new ResearchResponseParser(new JsonMapper());

  @Test
  void parsesTheJsonContract() {
    ResearchResponse response = parser.parse(Fixtures.read("research/valid.json"), 10);

    assertThat(response.skippedItems()).isZero();
    assertThat(response.items())
        .containsExactly(
            new NewsItemCandidate(
                "Spring Boot 4.1.3 veröffentlicht",
                "Das Patch-Release behebt mehrere Fehler. Es aktualisiert außerdem Abhängigkeiten.",
                "https://spring.example.test/blog/2026/11/20/spring-boot-4-1-3",
                LocalDate.of(2026, 11, 20)),
            new NewsItemCandidate(
                "Neue Java-Preview",
                "Ein neues Early-Access-Build ist verfügbar. Es enthält zwei Preview-Features.",
                "https://jdk.example.test/ea",
                null));
  }

  @Test
  void ignoresTextAndCodeFenceAroundTheJson() {
    ResearchResponse response =
        parser.parse(Fixtures.read("research/with-preamble-and-fence.txt"), 10);

    assertThat(response.items())
        .extracting(NewsItemCandidate::headline)
        .containsExactly("Release X");
  }

  @Test
  void emptyListMeansNoNews() {
    assertThat(parser.parse("{\"items\": []}", 10).items()).isEmpty();
  }

  @Test
  void dropsItemsThatBreakTheContract() {
    ResearchResponse response =
        parser.parse(Fixtures.read("research/mixed-invalid-items.json"), 10);

    assertThat(response.items())
        .extracting(NewsItemCandidate::headline)
        .containsExactly("Gültig", "Datum ungültig");
    assertThat(response.items().get(1).publishedAt()).isNull();
    assertThat(response.skippedItems()).isEqualTo(7);
  }

  @Test
  void keepsOnlyTheFirstMaxItems() {
    ResearchResponse response = parser.parse(Fixtures.read("research/too-many-items.json"), 10);

    assertThat(response.items()).hasSize(10);
    assertThat(response.items().getFirst().headline()).isEqualTo("Meldung 1");
    assertThat(response.items().getLast().headline()).isEqualTo("Meldung 10");
    assertThat(response.skippedItems()).isEqualTo(2);
  }

  @Test
  void tooLongUrlIsDropped() {
    String url = "https://news.example.test/" + "a".repeat(ResearchResponseParser.MAX_URL_LENGTH);
    String json =
        "{\"items\": [{\"headline\": \"h\", \"summary\": \"s\", \"url\": \"" + url + "\"}]}";

    assertThat(parser.parse(json, 10).items()).isEmpty();
  }

  @Test
  void invalidJsonIsAParseError() {
    assertThatThrownBy(() -> parser.parse(Fixtures.read("research/invalid-json.txt"), 10))
        .isInstanceOf(ResearchParseException.class)
        .hasMessageContaining("Invalid JSON");
  }

  @Test
  void missingItemsArrayIsAParseError() {
    assertThatThrownBy(() -> parser.parse(Fixtures.read("research/no-items.json"), 10))
        .isInstanceOf(ResearchParseException.class)
        .hasMessageContaining("items");
    assertThatThrownBy(() -> parser.parse("{\"items\": {}}", 10))
        .isInstanceOf(ResearchParseException.class);
  }

  @Test
  void answerWithoutJsonObjectIsAParseError() {
    assertThatThrownBy(() -> parser.parse("Leider nichts gefunden.", 10))
        .isInstanceOf(ResearchParseException.class)
        .hasMessageContaining("no JSON object");
    assertThatThrownBy(() -> parser.parse("} verdreht {", 10))
        .isInstanceOf(ResearchParseException.class);
    assertThatThrownBy(() -> parser.parse(null, 10))
        .isInstanceOf(ResearchParseException.class)
        .hasMessageContaining("empty");
  }
}
