package de.fuechsle.ainewsletter.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class IconKeysTest {

  @Test
  void listFromTheClasspathContainsTheDefaultAndNoComments() {
    IconKeys keys = new IconKeys();

    assertThat(keys.keys()).contains(IconKeys.DEFAULT, "ai", "security").doesNotHaveDuplicates();
    assertThat(keys.keys()).noneMatch(key -> key.startsWith("#") || key.isBlank());
  }

  @Test
  void unknownOrMissingKeyBecomesTheDefault() {
    IconKeys keys = new IconKeys(List.of(IconKeys.DEFAULT, "ai"));

    assertThat(keys.orDefault("ai")).isEqualTo("ai");
    assertThat(keys.orDefault("AI")).isEqualTo(IconKeys.DEFAULT);
    assertThat(keys.orDefault(null)).isEqualTo(IconKeys.DEFAULT);
  }

  @Test
  void listWithoutDefaultIsRejected() {
    assertThatThrownBy(() -> new IconKeys(List.of("ai"))).isInstanceOf(IllegalStateException.class);
  }
}
