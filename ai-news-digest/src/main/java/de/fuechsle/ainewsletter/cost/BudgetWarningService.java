package de.fuechsle.ainewsletter.cost;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.cost.CostReportService.CostRow;
import de.fuechsle.ainewsletter.cost.CostReportService.Grouping;
import de.fuechsle.ainewsletter.mail.MailService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Warns the operator once per calendar month when the spending reaches the warn threshold of the
 * budget (docs/12-defaults.md). Called at the end of every run, when its costs are recorded ({@code
 * budgetWarningListener}).
 *
 * <p>The month is marked in {@code budget_warning} in the same transaction that sends the mail: if
 * the mail fails, the mark is rolled back and the next run tries again. A failure never affects the
 * run; it is only logged.
 */
@Service
public class BudgetWarningService {

  private static final Logger log = LoggerFactory.getLogger(BudgetWarningService.class);
  private static final int TOP_TOPICS = 3;

  private final BudgetService budget;
  private final CostReportService report;
  private final MailService mail;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate newTransaction;
  private final String operator;

  public BudgetWarningService(
      BudgetService budget,
      CostReportService report,
      MailService mail,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactionManager,
      NewsDigestProperties properties) {
    this.budget = budget;
    this.report = report;
    this.mail = mail;
    this.jdbc = jdbc;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.operator = properties.mail().operator();
  }

  /**
   * Sends the warning if the threshold is reached and the month has none yet.
   *
   * @return whether a warning was sent
   */
  public boolean warnIfNeeded() {
    try {
      BudgetStatus status = budget.check();
      if (!status.warningReached()) {
        return false;
      }
      Boolean sent =
          newTransaction.execute(tx -> {
            int marked =
                jdbc.update(
                    "INSERT INTO budget_warning (month) VALUES (?) ON CONFLICT DO NOTHING",
                    status.month());
            if (marked == 0) {
              return false;
            }
            mail.sendText(
                operator,
                "[News Digest] Budget zu " + status.spentPercent() + " % ausgeschöpft",
                text(status, topTopics(status)));
            return true;
          });
      if (Boolean.TRUE.equals(sent)) {
        log.info("Budget warning for {} sent ({} %)", status.month(), status.spentPercent());
      }
      return Boolean.TRUE.equals(sent);
    } catch (RuntimeException e) {
      log.warn("Budget warning could not be sent, next run tries again: {}", e.getMessage());
      return false;
    }
  }

  private List<CostRow> topTopics(BudgetStatus status) {
    return report
        .costs(status.month(), status.month().plusMonths(1).minusDays(1), Grouping.TOPIC)
        .stream()
        .limit(TOP_TOPICS)
        .toList();
  }

  static String text(BudgetStatus status, List<CostRow> topics) {
    StringBuilder text = new StringBuilder();
    text.append("Das Monatsbudget ist zu ")
        .append(status.spentPercent())
        .append(" % ausgeschöpft (Warnschwelle ")
        .append(status.warnPercent())
        .append(" %).\n\n");
    text.append("Monat: ").append(status.month().toString(), 0, 7).append('\n');
    text.append("Ausgaben: ")
        .append(status.spentUsd().toPlainString())
        .append(" USD von ")
        .append(status.limitUsd().toPlainString())
        .append(" USD\n");
    if (!topics.isEmpty()) {
      text.append("\nTeuerste Topics:\n");
      for (CostRow topic : topics) {
        text.append("- ")
            .append(topic.key())
            .append(": ")
            .append(topic.costUsd().toPlainString())
            .append(" USD in ")
            .append(topic.runs())
            .append(topic.runs() == 1 ? " Lauf" : " Läufen");
        if (topic.estimatedUsd().signum() > 0) {
          text.append(" (davon geschätzt ")
              .append(topic.estimatedUsd().toPlainString())
              .append(')');
        }
        text.append('\n');
      }
    }
    text.append("\nIst das Budget erreicht, werden neue Läufe übersprungen (SKIPPED_BUDGET).\n");
    return text.toString();
  }
}
