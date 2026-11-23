package de.fuechsle.ainewsletter.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.support.TestProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

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

    assertThat(templates.research().version()).isEqualTo("v1");
    assertThat(templates.research().name()).isEqualTo("research");
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
