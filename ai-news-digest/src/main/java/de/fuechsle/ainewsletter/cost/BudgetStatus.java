package de.fuechsle.ainewsletter.cost;

import java.math.BigDecimal;

/**
 * Spending of the current calendar month against the monthly budget (USD).
 *
 * @param exhausted the budget is reached; no new run may call a model
 */
public record BudgetStatus(BigDecimal spentUsd, BigDecimal limitUsd, boolean exhausted) {}
