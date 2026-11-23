package de.fuechsle.ainewsletter.mail;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.cost.BudgetService;
import de.fuechsle.ainewsletter.cost.BudgetStatus;
import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.MailDeliveryStatus;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.MailDeliveryRepository;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain text mail to the operator ({@code NEWSDIGEST_MAIL_OPERATOR}) when a run needs attention.
 * The mail does not replace an alarm, because it can fail itself (docs/11-deployment-cicd.md); a
 * failure leads to job retries and then to an incident, without changing the status of the run.
 */
@Service
public class OperatorNotificationService {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

  private final DigestRunRepository runs;
  private final MailDeliveryRepository deliveries;
  private final BudgetService budget;
  private final MailService mailService;
  private final String operator;

  public OperatorNotificationService(
      DigestRunRepository runs,
      MailDeliveryRepository deliveries,
      BudgetService budget,
      MailService mailService,
      NewsDigestProperties properties) {
    this.runs = runs;
    this.deliveries = deliveries;
    this.budget = budget;
    this.mailService = mailService;
    this.operator = properties.mail().operator();
  }

  @Transactional(readOnly = true)
  public void notify(long runId, OperatorNotificationReason reason) {
    DigestRun run = runs.findWithTopic(runId).orElseThrow();
    String topic = run.getTopic().getName();
    mailService.sendText(
        operator, "[News Digest] Hinweis für den Betreiber – " + topic, text(run, reason));
  }

  String text(DigestRun run, OperatorNotificationReason reason) {
    StringBuilder text = new StringBuilder();
    text.append(reason.description()).append("\n\n");
    text.append("Topic: ").append(run.getTopic().getName()).append('\n');
    text.append("Lauf: ").append(run.getId()).append('\n');
    text.append("Zeitraum: ")
        .append(DATE.format(run.getPeriodFrom()))
        .append(" – ")
        .append(DATE.format(run.getPeriodTo()))
        .append('\n');
    if (run.getErrorMessage() != null) {
      text.append("Fehler: ").append(run.getErrorMessage()).append('\n');
    }
    switch (reason) {
      case BUDGET_EXHAUSTED -> {
        BudgetStatus status = budget.check();
        text.append("Ausgaben im Monat: ")
            .append(status.spentUsd().toPlainString())
            .append(" USD von ")
            .append(status.limitUsd().toPlainString())
            .append(" USD\n");
      }
      case MAIL_PARTIALLY_FAILED ->
          text.append("Zugestellt: ")
              .append(deliveries.countByDigestRunIdAndStatus(run.getId(), MailDeliveryStatus.SENT))
              .append(", fehlgeschlagen: ")
              .append(
                  deliveries.countByDigestRunIdAndStatus(run.getId(), MailDeliveryStatus.FAILED))
              .append(" (Details im Mail-Protokoll des Laufs)\n");
      default -> {
        // no further details
      }
    }
    return text.toString();
  }
}
