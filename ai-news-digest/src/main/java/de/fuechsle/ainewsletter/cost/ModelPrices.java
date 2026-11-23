package de.fuechsle.ainewsletter.cost;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Prices of a model in USD, as maintained in {@code ai_model}.
 *
 * @param inputPerMtok price per 1 million input tokens
 * @param outputPerMtok price per 1 million output tokens
 * @param perSearchCall price per web search, {@code null} if not maintained
 */
public record ModelPrices(
    BigDecimal inputPerMtok, BigDecimal outputPerMtok, BigDecimal perSearchCall) {

  public ModelPrices {
    Objects.requireNonNull(inputPerMtok, "inputPerMtok");
    Objects.requireNonNull(outputPerMtok, "outputPerMtok");
  }
}
