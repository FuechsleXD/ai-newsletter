package de.fuechsle.ainewsletter.cost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Calculates the cost of model calls in USD from token counts, web searches and the model prices at
 * the time of the run (price snapshot, docs/03-datenmodell.md). Result scale matches {@code
 * numeric(12,6)}.
 */
@Component
public class CostCalculator {

  public static final int SCALE = 6;

  private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

  /**
   * Cost of one call. With a long context tier and more input tokens than its threshold, the tier
   * prices apply to all tokens of the call. For calls with web search the provider reports the
   * input tokens summed over its internal steps; comparing that sum with the threshold is rather
   * too high than too low.
   *
   * @throws IllegalArgumentException if a count is negative or searches were made but the model has
   *     no search price
   */
  public BigDecimal cost(
      ModelPrices prices, long inputTokens, long outputTokens, long webSearches) {
    if (inputTokens < 0 || outputTokens < 0 || webSearches < 0) {
      throw new IllegalArgumentException("Token and search counts must not be negative");
    }
    if (webSearches > 0 && prices.perSearchCall() == null) {
      throw new IllegalArgumentException(
          "Web searches were made, but the model has no price per search call");
    }
    boolean longPrompt =
        prices.longContext() != null && inputTokens > prices.longContext().thresholdTokens();
    BigDecimal inputPrice =
        longPrompt ? prices.longContext().inputPerMtok() : prices.inputPerMtok();
    BigDecimal outputPrice =
        longPrompt ? prices.longContext().outputPerMtok() : prices.outputPerMtok();
    BigDecimal tokens =
        inputPrice
            .multiply(BigDecimal.valueOf(inputTokens))
            .add(outputPrice.multiply(BigDecimal.valueOf(outputTokens)))
            .divide(ONE_MILLION, SCALE + 4, RoundingMode.HALF_UP);
    BigDecimal searches =
        webSearches == 0
            ? BigDecimal.ZERO
            : prices.perSearchCall().multiply(BigDecimal.valueOf(webSearches));
    return tokens.add(searches).setScale(SCALE, RoundingMode.HALF_UP);
  }
}
