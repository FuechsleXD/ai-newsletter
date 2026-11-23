package de.fuechsle.ainewsletter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AiModelTest {

  @Test
  void temperatureIsRejectedForModelsThatDoNotAcceptIt() {
    AiModel model = model();

    assertThatThrownBy(() -> model.setTemperature(new BigDecimal("0.5")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not accept a temperature");
    assertThat(model.getTemperature()).isNull();
  }

  @Test
  void temperatureCanBeSetWhenSupportedAndRemovedAnytime() {
    AiModel model = model();
    model.setSupportsTemperature(true);

    model.setTemperature(new BigDecimal("0"));
    model.setTemperature(new BigDecimal("2"));
    assertThat(model.getTemperature()).isEqualByComparingTo("2");

    model.setTemperature(null);
    model.setSupportsTemperature(false);
    assertThat(model.getTemperature()).isNull();
    assertThat(model.isSupportsTemperature()).isFalse();
  }

  @Test
  void temperatureMustBeBetweenZeroAndTwo() {
    AiModel model = model();
    model.setSupportsTemperature(true);

    assertThatThrownBy(() -> model.setTemperature(new BigDecimal("-0.1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> model.setTemperature(new BigDecimal("2.01")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void supportCannotBeSwitchedOffWhileATemperatureIsMaintained() {
    AiModel model = model();
    model.setSupportsTemperature(true);
    model.setTemperature(new BigDecimal("0.7"));

    assertThatThrownBy(() -> model.setSupportsTemperature(false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("remove it");
    assertThat(model.isSupportsTemperature()).isTrue();
  }

  private static AiModel model() {
    AiProvider provider = new AiProvider("ANTHROPIC", "Anthropic", "newsdigest/test/ai/anthropic");
    // Test values only, not real model IDs or prices
    return new AiModel(
        provider, "test-model-1", "Test", true, true, BigDecimal.ONE, BigDecimal.TEN);
  }
}
