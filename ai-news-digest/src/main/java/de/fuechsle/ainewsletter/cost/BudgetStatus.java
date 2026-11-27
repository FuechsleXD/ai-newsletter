package de.fuechsle.ainewsletter.cost;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Spending of the current calendar month against the monthly budget (USD).
 *
 * @param month first day of the calendar month (Europe/Berlin)
 * @param warnPercent warn threshold in whole percent of the limit
 * @param exhausted the budget is reached; no new run may call a model
 */
public record BudgetStatus(
    LocalDate month, BigDecimal spentUsd, BigDecimal limitUsd, int warnPercent, boolean exhausted) {

  /** Whether the spending reached the warn threshold. */
  public boolean warningReached() {
    return spentUsd
            .multiply(BigDecimal.valueOf(100))
            .compareTo(limitUsd.multiply(BigDecimal.valueOf(warnPercent)))
        >= 0;
  }

  /** Spending in whole percent of the limit, rounded down. */
  public int spentPercent() {
    if (limitUsd.signum() == 0) {
      return 100;
    }
    return spentUsd
        .multiply(BigDecimal.valueOf(100))
        .divide(limitUsd, 0, java.math.RoundingMode.DOWN)
        .intValue();
  }
}
