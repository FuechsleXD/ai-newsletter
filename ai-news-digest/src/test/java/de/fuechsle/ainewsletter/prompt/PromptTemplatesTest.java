package de.fuechsle.ainewsletter.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.support.TestProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

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

    assertThat(templates.research().version()).isEqualTo("v6");
    assertThat(templates.research().name()).isEqualTo("research");
  }

  @ParameterizedTest
  @CsvSource({
    "research, v2",
    "research, v3",
    "research, v4",
    "research, v5",
    "research, v6",
    "aggregate, v2",
    "aggregate, v3",
    "aggregate, v4",
    "repair-json, v2"
  })
  void newerVersionsKeepThePlaceholdersAndTheOutputFormatOfV1(String name, String version) {
    PromptTemplate v1 = templates.load(name, "v1");
    PromptTemplate newer = templates.load(name, version);

    // The services pass the same values to every version; the renderer rejects extra ones
    assertThat(PromptRenderer.placeholders(newer.text()))
        .containsExactlyInAnyOrderElementsOf(PromptRenderer.placeholders(v1.text()));
    // Same JSON structure; the example values may be in another language
    assertThat(jsonShape(newer)).isEqualTo(jsonShape(v1));
  }

  @Test
  void outputSectionOfTheEnglishVersionsStartsAtOutput() {
    assertThat(templates.load("research", "v4").outputSection())
        .startsWith("OUTPUT" + "\nReply only");
    assertThat(new PromptTemplate("x", "v9", "TASK\nOUTPUT\n{}\n").outputSection())
        .isEqualTo("OUTPUT\n{}");
  }

  /** The example answer of the output section with every text value replaced by "". */
  private static String jsonShape(PromptTemplate template) {
    String section = template.outputSection();
    int start = section.indexOf('{');
    // repair-json has no output section of its own (it shows the one of the failed prompt)
    if (section.equals(template.text()) || start < 0) {
      return "";
    }
    JsonNode example =
        new JsonMapper().readTree(section.substring(start, section.lastIndexOf('}') + 1));
    return blank(example).toString();
  }

  private static JsonNode blank(JsonNode node) {
    if (node.isString()) {
      return JsonNodeFactory.instance.stringNode("");
    }
    if (node.isObject()) {
      ObjectNode copy = JsonNodeFactory.instance.objectNode();
      node.properties().forEach(entry -> copy.set(entry.getKey(), blank(entry.getValue())));
      return copy;
    }
    if (node.isArray()) {
      ArrayNode copy = JsonNodeFactory.instance.arrayNode();
      node.forEach(element -> copy.add(blank(element)));
      return copy;
    }
    return node;
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
