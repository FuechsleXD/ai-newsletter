package de.fuechsle.ainewsletter.mail;

/** Result of sending the digest of a run (process variable {@code mailOutcome}). */
public enum MailOutcome {
  /** Every active recipient got the mail. */
  ALL_SENT,
  /** At least one recipient got the mail, at least one failed for good; the operator is told. */
  PARTIALLY_SENT
}
