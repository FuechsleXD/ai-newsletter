package de.fuechsle.ainewsletter.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptRendererTest {

  private final PromptRenderer renderer = new PromptRenderer();

  @Test
  void replacesAllPlaceholdersAndKeepsJsonBraces() {
    PromptTemplate template =
        template("Thema {{topic}} von {{from}} bis {{to}}.\n{ \"items\": [ { \"a\": 1 } ] }");

    String result = renderer.render(template, Map.of("topic", "Java", "from", "A", "to", "B"));

    assertThat(result).isEqualTo("Thema Java von A bis B.\n{ \"items\": [ { \"a\": 1 } ] }");
  }

  @Test
  void samePlaceholderMayAppearSeveralTimes() {
    assertThat(renderer.render(template("{{x}} und {{x}}"), Map.of("x", "1"))).isEqualTo("1 und 1");
  }

  @Test
  void valuesAreInsertedLiterally() {
    PromptTemplate template = template("A {{a}} B {{b}}");

    String result = renderer.render(template, Map.of("a", "{{b}}", "b", "$1 \\ C:\\pfad"));

    assertThat(result).isEqualTo("A {{b}} B $1 \\ C:\\pfad");
  }

  @Test
  void missingValueIsAnError() {
    assertThatThrownBy(() -> renderer.render(template("{{a}} {{b}}"), Map.of("a", "1")))
        .isInstanceOf(PromptException.class)
        .hasMessageContaining("test/v9")
        .hasMessageContaining("no value")
        .hasMessageContaining("[b]");
  }

  @Test
  void valueWithoutPlaceholderIsAnError() {
    assertThatThrownBy(() -> renderer.render(template("{{a}}"), Map.of("a", "1", "extra", "2")))
        .isInstanceOf(PromptException.class)
        .hasMessageContaining("unknown placeholder")
        .hasMessageContaining("[extra]");
  }

  @Test
  void nullValueIsAnError() {
    Map<String, String> values = new HashMap<>();
    values.put("a", null);

    assertThatThrownBy(() -> renderer.render(template("{{a}}"), values))
        .isInstanceOf(PromptException.class)
        .hasMessageContaining("is null");
  }

  @Test
  void textWithoutPlaceholdersIsReturnedUnchanged() {
    assertThat(renderer.render(template("{ \"x\": {} }"), Map.of())).isEqualTo("{ \"x\": {} }");
  }

  private static PromptTemplate template(String text) {
    return new PromptTemplate("test", "v9", text);
  }
}
