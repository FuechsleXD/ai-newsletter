package de.fuechsle.ainewsletter.process;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.cost.BudgetService;
import de.fuechsle.ainewsletter.cost.CostReportService;
import de.fuechsle.ainewsletter.cost.CostReportService.CostRow;
import de.fuechsle.ainewsletter.cost.CostReportService.Grouping;
import de.fuechsle.ainewsletter.support.Mailpit;
import de.fuechsle.ainewsletter.support.PostgresTestSupport;
import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Cost report and budget warning through the whole process (fake provider, Mailpit). */
class BudgetProcessTest extends ProcessTestSupport {

  private static final String WARNING_SUBJECT = "[News Digest] Budget zu";

  @Autowired private DigestProcessStarter starter;
  @Autowired private CostReportService report;
  @Autowired private BudgetService budget;

  @Test
  void costsOfARunAreReportedPerTopicModelAndMonthWithEstimatesApart() {
    String suffix = unique();
    long researcher = model("ok-research-" + suffix);
    long aggregator = model("ok-aggregator-" + suffix);
    String topicName = "Kosten " + suffix;
    long topic = topic(topicName, aggregator, researcher);

    String instance = starter.start(topic);
    awaitEnd(instance);
    long runId = runId(instance);
    // Mark the research cost as estimated, as for a call that broke off
    jdbc.update("UPDATE provider_result SET cost_estimated = true WHERE digest_run_id = ?", runId);
    BigDecimal research =
        jdbc.queryForObject(
            "SELECT cost_usd FROM provider_result WHERE digest_run_id = ?",
            BigDecimal.class,
            runId);
    BigDecimal aggregation =
        jdbc.queryForObject(
            "SELECT cost_usd FROM aggregation_result WHERE digest_run_id = ?",
            BigDecimal.class,
            runId);
    LocalDate today = LocalDate.now(BudgetService.BUDGET_ZONE);

    CostRow byTopic = row(report.costs(today, today, Grouping.TOPIC), topicName);
    assertThat(byTopic.costUsd()).isEqualByComparingTo(research.add(aggregation));
    assertThat(byTopic.estimatedUsd()).isEqualByComparingTo(research);
    assertThat(byTopic.runs()).isEqualTo(1);
    assertThat(byTopic.calls()).isEqualTo(2);
    assertThat(byTopic.inputTokens()).isPositive();
    assertThat(byTopic.webSearches()).isPositive();

    List<CostRow> byModel = report.costs(today, today, Grouping.MODEL);
    assertThat(row(byModel, "ok-research-" + suffix).costUsd()).isEqualByComparingTo(research);
    assertThat(row(byModel, "ok-aggregator-" + suffix).costUsd()).isEqualByComparingTo(aggregation);
    assertThat(report.costs(today, today, Grouping.MONTH))
        .extracting(CostRow::key)
        .containsExactly(YearMonth.from(today).toString());
    // Runs of another day are not counted
    assertThat(report.costs(today.minusDays(3), today.minusDays(1), Grouping.TOPIC))
        .extracting(CostRow::key)
        .doesNotContain(topicName);
  }

  @Test
  void runThatReachesTheWarnThresholdWarnsTheOperatorOncePerMonth() {
    jdbc.update("DELETE FROM budget_warning");
    // Limit just above the spending so far: this run reaches 100 % and also the warn threshold
    BigDecimal spent = budget.check().spentUsd();
    jdbc.update(
        "INSERT INTO budget (month_limit_usd, warn_percent, active) VALUES (?, 100, true)",
        spent.add(new BigDecimal("0.000001")));
    try {
      int before = warnings();
      String suffix = unique();
      long topic =
          topic("Warnung " + suffix, model("ok-aggregator-" + suffix), model("ok-" + suffix));

      String first = starter.start(topic);
      awaitEnd(first);
      assertThat(runStatus(first)).isEqualTo("COMPLETED");
      assertThat(awaitWarnings(before + 1)).isEqualTo(before + 1);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_warning", Integer.class))
          .isEqualTo(1);

      // The next run is skipped for the budget and does not warn a second time
      String second = starter.start(topic);
      awaitEnd(second);
      assertThat(runStatus(second)).isEqualTo("SKIPPED_BUDGET");
      assertThat(warnings()).isEqualTo(before + 1);
    } finally {
      jdbc.update("UPDATE budget SET active = false WHERE active");
      jdbc.update("DELETE FROM budget_warning");
    }
  }

  private static CostRow row(List<CostRow> rows, String key) {
    return rows.stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
  }

  private static int warnings() {
    return (int)
        Mailpit.mailsTo(PostgresTestSupport.MAIL_OPERATOR).stream()
            .filter(mail -> mail.subject().startsWith(WARNING_SUBJECT))
            .count();
  }

  private static int awaitWarnings(int count) {
    long deadline = System.currentTimeMillis() + 10_000;
    int found = warnings();
    while (found < count && System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      found = warnings();
    }
    return found;
  }
}
