package de.fuechsle.ainewsletter.mail;

/**
 * Why the operator is informed (input parameter {@code notificationReason} of the BPMN tasks
 * "Betreiber informieren"). The descriptions are German, because the mail is read by the operator.
 */
public enum OperatorNotificationReason {
  BUDGET_EXHAUSTED("Das Monatsbudget ist erreicht; der Lauf wurde ohne KI-Aufrufe übersprungen."),
  NO_MODEL_SUCCEEDED(
      "Kein Recherche-Modell hat ein Ergebnis geliefert; der Lauf ist fehlgeschlagen."),
  AGGREGATION_FAILED("Die Aggregation ist fehlgeschlagen; der Lauf ist fehlgeschlagen."),
  NO_RECIPIENTS("Das Topic hat keine aktiven Empfänger; der Lauf ist fehlgeschlagen."),
  MAIL_PARTIALLY_FAILED(
      "Der Digest wurde verschickt, aber nicht alle Empfänger haben die Mail erhalten.");

  private final String description;

  OperatorNotificationReason(String description) {
    this.description = description;
  }

  public String description() {
    return description;
  }
}
