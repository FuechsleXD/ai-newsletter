package de.fuechsle.ainewsletter.cost;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.repository.BudgetRepository;
import de.fuechsle.ainewsletter.support.TestProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class BudgetServiceTest {

  // 2026-01-31 23:30 UTC is already February in Berlin
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-31T23:30:00Z"), ZoneOffset.UTC);

  private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
  private final BudgetRepository budgets = Mockito.mock(BudgetRepository.class);

  @Test
  void spendingBelowTheDefaultLimitIsOk() {
    spent(List.of(new BigDecimal("9.99")));
    Mockito.when(budgets.findByActiveTrue()).thenReturn(Optional.empty());

    BudgetStatus status = service().check();

    assertThat(status.exhausted()).isFalse();
    assertThat(status.limitUsd()).isEqualByComparingTo("10");
    assertThat(status.spentUsd()).isEqualByComparingTo("9.99");
  }

  @Test
  void reachingTheActiveBudgetExhaustsIt() {
    spent(List.of(new BigDecimal("5")));
    Mockito.when(budgets.findByActiveTrue())
        .thenReturn(
            Optional.of(new de.fuechsle.ainewsletter.domain.Budget(BigDecimal.valueOf(5), 80)));

    assertThat(service().check().exhausted()).isTrue();
  }

  @Test
  void monthWithoutCostsHasSpentNothingAndUsesTheBerlinCalendarMonth() {
    spent(List.of());
    Mockito.when(budgets.findByActiveTrue()).thenReturn(Optional.empty());

    BudgetStatus status = service().check();

    assertThat(status.spentUsd()).isEqualByComparingTo(BigDecimal.ZERO);
    Mockito.verify(jdbc)
        .queryForList(
            Mockito.anyString(),
            Mockito.eq(BigDecimal.class),
            Mockito.eq(java.time.LocalDate.of(2026, 2, 1)));
  }

  private void spent(List<BigDecimal> values) {
    Mockito.when(
            jdbc.queryForList(
                Mockito.anyString(), Mockito.eq(BigDecimal.class), Mockito.any(Object[].class)))
        .thenReturn(values);
  }

  private BudgetService service() {
    return new BudgetService(jdbc, budgets, TestProperties.with(Map.of()), CLOCK);
  }
}
