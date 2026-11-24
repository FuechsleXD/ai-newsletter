package de.fuechsle.ainewsletter.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.support.TestProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PromptTemplatesTest {

  private final PromptTemplates templates = new PromptTemplates(TestProperties.defaults());

  @Test
  void activeVersionsAreLoadedWithTheDocumentedPlaceholders() {
    // Placeholders as documented in src/main/resources/prompts/README.md
    assertThat(PromptRenderer.placeholders(templates.research().text()))
        .containsExactlyInAnyOrder(
            "topicName",
            "topicDescription",
            "periodFrom",
            "periodTo",
            "timezone",
            "maxItems",
            "language");
    assertThat(PromptRenderer.placeholders(templates.aggregate().text()))
        .contains("topicName", "newsItemsJson", "previousHeadlines");
    assertThat(PromptRenderer.placeholders(templates.repairJson().text()))
        .containsExactlyInAnyOrder("parseError", "expectedFormat", "previousResponse");

    assertThat(templates.research().version()).isEqualTo("v3");
    assertThat(templates.research().name()).isEqualTo("research");
  }

  @ParameterizedTest
  @CsvSource({"research, v2", "research, v3", "aggregate, v2"})
  void newerVersionsKeepThePlaceholdersAndTheOutputFormatOfV1(String name, String version) {
    PromptTemplate v1 = templates.load(name, "v1");
    PromptTemplate newer = templates.load(name, version);

    // The services pass the same values to every version; the renderer rejects extra ones
    assertThat(PromptRenderer.placeholders(newer.text()))
        .containsExactlyInAnyOrderElementsOf(PromptRenderer.placeholders(v1.text()));
    assertThat(newer.outputSection()).isEqualTo(v1.outputSection());
  }

  @Test
  void configuredVersionIsUsed() {
    PromptTemplates other =
        new PromptTemplates(TestProperties.with(Map.of("newsdigest.prompts.research", "v99")));

    assertThatThrownBy(other::research)
        .isInstanceOf(PromptException.class)
        .hasMessageContaining("prompts/research/v99.txt");
  }

  @Test
  void templatesAreCached() {
    assertThat(templates.research()).isSameAs(templates.research());
  }

  @Test
  void pathTraversalIsRejected() {
    assertThatThrownBy(() -> templates.load("../secret", "v1"))
        .isInstanceOf(PromptException.class)
        .hasMessageContaining("Invalid prompt name");
    assertThatThrownBy(() -> templates.load("research", "v1/../../x"))
        .isInstanceOf(PromptException.class);
  }

  @Test
  void outputSectionStartsAtAusgabeAndFallsBackToTheWholeText() {
    assertThat(new PromptTemplate("x", "v1", "Aufgabe\nAUSGABE\n{}\n").outputSection())
        .isEqualTo("AUSGABE\n{}");
    assertThat(new PromptTemplate("x", "v1", "nur Text").outputSection()).isEqualTo("nur Text");
  }
}
