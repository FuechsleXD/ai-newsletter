package de.fuechsle.ainewsletter.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostCalculatorTest {

  private final CostCalculator calculator = new CostCalculator();

  // Test prices only, not real provider prices
  private final ModelPrices prices =
      new ModelPrices(new BigDecimal("3"), new BigDecimal("15"), new BigDecimal("0.01"));

  @Test
  void tokensAndSearchesArePriced() {
    // 12,000 * 3 / 1M = 0.036; 2,500 * 15 / 1M = 0.0375; 4 * 0.01 = 0.04
    assertThat(calculator.cost(prices, 12_000, 2_500, 4)).isEqualTo(new BigDecimal("0.113500"));
  }

  @Test
  void resultHasSixDecimalsAndRoundsHalfUp() {
    ModelPrices cheap = new ModelPrices(new BigDecimal("0.1"), new BigDecimal("0.5"), null);

    // 7 * 0.1 / 1M = 0.0000007 -> 0.000001
    assertThat(calculator.cost(cheap, 7, 0, 0)).isEqualTo(new BigDecimal("0.000001"));
    // 4 * 0.1 / 1M = 0.0000004 -> 0.000000
    assertThat(calculator.cost(cheap, 4, 0, 0)).isEqualTo(new BigDecimal("0.000000"));
  }

  @Test
  void noUsageCostsNothing() {
    assertThat(calculator.cost(prices, 0, 0, 0)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void searchesWithoutSearchPriceAreRejected() {
    ModelPrices noSearchPrice = new ModelPrices(BigDecimal.ONE, BigDecimal.ONE, null);

    assertThat(calculator.cost(noSearchPrice, 10, 10, 0)).isNotNull();
    assertThatThrownBy(() -> calculator.cost(noSearchPrice, 10, 10, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price per search");
  }

  @Test
  void negativeCountsAreRejected() {
    assertThatThrownBy(() -> calculator.cost(prices, -1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> calculator.cost(prices, 0, -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> calculator.cost(prices, 0, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pricesAreRequired() {
    assertThatThrownBy(() -> new ModelPrices(null, BigDecimal.ONE, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ModelPrices(BigDecimal.ONE, null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
