package de.fuechsle.ainewsletter.cost;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.domain.Budget;
import de.fuechsle.ainewsletter.repository.BudgetRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the monthly budget before a run (golden rule 5). The costs of the calendar month in
 * Europe/Berlin come from the view {@code monthly_cost}; the limit from the active {@code budget}
 * row or, without one, from the default.
 */
@Service
public class BudgetService {

  /** Months are calendar months in this zone (docs/03-datenmodell.md). */
  public static final ZoneId BUDGET_ZONE = ZoneId.of("Europe/Berlin");

  private final JdbcTemplate jdbc;
  private final BudgetRepository budgets;
  private final BigDecimal defaultLimit;
  private final Clock clock;

  public BudgetService(
      JdbcTemplate jdbc, BudgetRepository budgets, NewsDigestProperties properties, Clock clock) {
    this.jdbc = jdbc;
    this.budgets = budgets;
    this.defaultLimit = properties.budget().monthlyLimitUsd();
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public BudgetStatus check() {
    LocalDate month = YearMonth.now(clock.withZone(BUDGET_ZONE)).atDay(1);
    List<BigDecimal> spent =
        jdbc.queryForList(
            "SELECT cost_usd FROM monthly_cost WHERE month = ?", BigDecimal.class, month);
    BigDecimal spentUsd = spent.isEmpty() ? BigDecimal.ZERO : spent.getFirst();
    BigDecimal limitUsd =
        budgets.findByActiveTrue().map(Budget::getMonthLimitUsd).orElse(defaultLimit);
    return new BudgetStatus(spentUsd, limitUsd, spentUsd.compareTo(limitUsd) >= 0);
  }
}
