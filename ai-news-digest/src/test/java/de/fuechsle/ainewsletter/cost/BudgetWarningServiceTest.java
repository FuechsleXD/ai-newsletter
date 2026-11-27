package de.fuechsle.ainewsletter.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fuechsle.ainewsletter.cost.CostReportService.CostRow;
import de.fuechsle.ainewsletter.mail.MailService;
import de.fuechsle.ainewsletter.support.PostgresTestSupport;
import de.fuechsle.ainewsletter.support.TestProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.PlatformTransactionManager;

class BudgetWarningServiceTest {

  private static final LocalDate MONTH = LocalDate.of(2026, 11, 1);

  private final BudgetService budget = mock(BudgetService.class);
  private final CostReportService report = mock(CostReportService.class);
  private final MailService mail = mock(MailService.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

  private final BudgetWarningService service =
      new BudgetWarningService(budget, report, mail, jdbc, transactions, TestProperties.defaults());

  @Test
  void belowTheThresholdNothingHappens() {
    when(budget.check()).thenReturn(status("7.99"));

    assertThat(service.warnIfNeeded()).isFalse();
    verify(jdbc, never()).update(anyString(), any(Object[].class));
    verify(mail, never()).sendText(anyString(), anyString(), anyString());
  }

  @Test
  void reachingTheThresholdSendsOneWarningForTheMonth() {
    when(budget.check()).thenReturn(status("8.00"));
    when(jdbc.update(anyString(), eq(MONTH))).thenReturn(1);
    when(report.costs(MONTH, LocalDate.of(2026, 11, 30), CostReportService.Grouping.TOPIC))
        .thenReturn(List.of(row("AI for Java", "6.50", "0.40", 5)));

    assertThat(service.warnIfNeeded()).isTrue();

    verify(mail)
        .sendText(
            eq(PostgresTestSupport.MAIL_OPERATOR),
            eq("[News Digest] Budget zu 80 % ausgeschöpft"),
            anyString());
  }

  @Test
  void alreadyWarnedMonthSendsNothing() {
    when(budget.check()).thenReturn(status("9.50"));
    when(jdbc.update(anyString(), eq(MONTH))).thenReturn(0);

    assertThat(service.warnIfNeeded()).isFalse();
    verify(mail, never()).sendText(anyString(), anyString(), anyString());
  }

  @Test
  void failedMailIsOnlyLoggedAndTriedAgainLater() {
    when(budget.check()).thenReturn(status("8.00"));
    when(jdbc.update(anyString(), eq(MONTH))).thenReturn(1);
    when(report.costs(any(), any(), any())).thenReturn(List.of());
    doThrow(new MailSendException("smtp down"))
        .when(mail)
        .sendText(anyString(), anyString(), anyString());

    // The mark of the month is rolled back with the transaction, the run goes on
    assertThat(service.warnIfNeeded()).isFalse();
    verify(transactions).rollback(any());
  }

  @Test
  void textNamesSpendingThresholdAndTheMostExpensiveTopics() {
    String text =
        BudgetWarningService.text(
            status("8.40"),
            List.of(row("AI for Java", "6.50", "0.40", 5), row("Cloud", "1.90", "0", 1)));

    assertThat(text)
        .contains("zu 84 % ausgeschöpft (Warnschwelle 80 %)")
        .contains("Monat: 2026-11")
        .contains("Ausgaben: 8.40 USD von 10 USD")
        .contains("- AI for Java: 6.50 USD in 5 Läufen (davon geschätzt 0.40)")
        .contains("- Cloud: 1.90 USD in 1 Lauf\n")
        .contains("SKIPPED_BUDGET");
  }

  @Test
  void statusComputesThresholdAndPercent() {
    assertThat(status("7.99").warningReached()).isFalse();
    assertThat(status("8").warningReached()).isTrue();
    assertThat(status("8.49").spentPercent()).isEqualTo(84);
    assertThat(new BudgetStatus(MONTH, BigDecimal.ONE, BigDecimal.ZERO, 80, true).spentPercent())
        .isEqualTo(100);
  }

  private static BudgetStatus status(String spent) {
    BigDecimal spentUsd = new BigDecimal(spent);
    BigDecimal limit = BigDecimal.TEN;
    return new BudgetStatus(MONTH, spentUsd, limit, 80, spentUsd.compareTo(limit) >= 0);
  }

  private static CostRow row(String key, String cost, String estimated, long runs) {
    return new CostRow(key, new BigDecimal(cost), new BigDecimal(estimated), runs, runs, 0, 0, 0);
  }
}
