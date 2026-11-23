package de.fuechsle.ainewsletter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TextShortenerTest {

  @Test
  void shortTextStaysUnchangedButStripped() {
    assertThat(TextShortener.shorten("  Kurzer Text  ", 20)).isEqualTo("Kurzer Text");
    assertThat(TextShortener.shorten("exakt zehn", 10)).isEqualTo("exakt zehn");
  }

  @Test
  void longTextIsCutAtAWordBoundaryWithEllipsis() {
    String shortened = TextShortener.shorten("Ein neues Release bringt Verbesserungen", 20);

    assertThat(shortened).isEqualTo("Ein neues Release…").hasSizeLessThanOrEqualTo(20);
  }

  @Test
  void textWithoutUsefulWordBoundaryIsCutHard() {
    assertThat(TextShortener.shorten("Donaudampfschifffahrtsgesellschaft", 10))
        .isEqualTo("Donaudamp…")
        .hasSize(10);
  }

  @Test
  void nullBecomesEmptyAndTooSmallLimitIsRejected() {
    assertThat(TextShortener.shorten(null, 10)).isEmpty();
    assertThatThrownBy(() -> TextShortener.shorten("x", 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
