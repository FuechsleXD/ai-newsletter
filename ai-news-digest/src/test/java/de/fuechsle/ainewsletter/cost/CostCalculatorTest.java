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
  void longPromptsAreBilledWithTheTierPricesForTheWholeCall() {
    ModelPrices tiered =
        new ModelPrices(
            new BigDecimal("0.10"),
            new BigDecimal("0.50"),
            new BigDecimal("0.01"),
            new ModelPrices.LongContext(100_000, new BigDecimal("0.50"), new BigDecimal("2.50")));

    // At the threshold the normal prices apply: 100,000 * 0.10 / 1M + 1,000 * 0.50 / 1M
    assertThat(calculator.cost(tiered, 100_000, 1_000, 0)).isEqualTo(new BigDecimal("0.010500"));
    // Above it all tokens of the call: 100,001 * 0.50 / 1M + 1,000 * 2.50 / 1M + 2 searches
    assertThat(calculator.cost(tiered, 100_001, 1_000, 2)).isEqualTo(new BigDecimal("0.072501"));
    // Without a tier the normal prices apply to every length
    assertThat(calculator.cost(prices, 400_000, 0, 0)).isEqualTo(new BigDecimal("1.200000"));
  }

  @Test
  void tierIsOnlyComplete() {
    assertThat(ModelPrices.LongContext.of(null, BigDecimal.ONE, BigDecimal.ONE)).isNull();
    assertThat(ModelPrices.LongContext.of(100, BigDecimal.ONE, null)).isNull();
    assertThat(ModelPrices.LongContext.of(100, BigDecimal.ONE, BigDecimal.TEN).thresholdTokens())
        .isEqualTo(100);
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
