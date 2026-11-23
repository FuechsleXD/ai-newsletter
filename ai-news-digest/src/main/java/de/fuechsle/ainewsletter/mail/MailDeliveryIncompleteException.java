package de.fuechsle.ainewsletter.mail;

/**
 * Not every recipient got the digest mail. Thrown so that the engine retries the job; in the last
 * attempt only if no recipient got the mail, which leads to an incident.
 */
public class MailDeliveryIncompleteException extends RuntimeException {

  public MailDeliveryIncompleteException(long runId, int failedRecipients) {
    super(
        "Digest mail of run "
            + runId
            + " could not be delivered to "
            + failedRecipients
            + " recipient(s)");
  }
}
