package de.fuechsle.ainewsletter.domain;

/** Delivery state of a digest mail per recipient. */
public enum MailDeliveryStatus {
  PENDING,
  SENT,
  FAILED
}
