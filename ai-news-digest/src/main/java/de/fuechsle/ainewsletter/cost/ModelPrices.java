package de.fuechsle.ainewsletter.cost;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Prices of a model in USD, as maintained in {@code ai_model}.
 *
 * @param inputPerMtok price per 1 million input tokens
 * @param outputPerMtok price per 1 million output tokens
 * @param perSearchCall price per web search, {@code null} if not maintained
 * @param longContext higher prices for calls with a long prompt, {@code null} if the model has none
 */
public record ModelPrices(
    BigDecimal inputPerMtok,
    BigDecimal outputPerMtok,
    BigDecimal perSearchCall,
    LongContext longContext) {

  public ModelPrices {
    Objects.requireNonNull(inputPerMtok, "inputPerMtok");
    Objects.requireNonNull(outputPerMtok, "outputPerMtok");
  }

  /** Prices without a tier for long prompts. */
  public ModelPrices(BigDecimal inputPerMtok, BigDecimal outputPerMtok, BigDecimal perSearchCall) {
    this(inputPerMtok, outputPerMtok, perSearchCall, null);
  }

  /**
   * Price tier for long prompts (open point 6): above {@code thresholdTokens} input tokens of one
   * call, these prices apply to all tokens of that call.
   */
  public record LongContext(
      long thresholdTokens, BigDecimal inputPerMtok, BigDecimal outputPerMtok) {

    public LongContext {
      Objects.requireNonNull(inputPerMtok, "inputPerMtok");
      Objects.requireNonNull(outputPerMtok, "outputPerMtok");
    }

    /** The tier of a model row, {@code null} if not all three values are maintained. */
    public static LongContext of(Integer thresholdTokens, BigDecimal input, BigDecimal output) {
      return thresholdTokens == null || input == null || output == null
          ? null
          : new LongContext(thresholdTokens, input, output);
    }
  }
}
