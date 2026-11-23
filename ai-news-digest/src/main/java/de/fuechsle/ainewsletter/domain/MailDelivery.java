package de.fuechsle.ainewsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Delivery log of a digest mail per run and recipient. */
@Entity
@Table(name = "mail_delivery")
public class MailDelivery {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "digest_run_id", nullable = false)
  private DigestRun digestRun;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false)
  private Recipient recipient;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private MailDeliveryStatus status;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  protected MailDelivery() {}

  public MailDelivery(DigestRun digestRun, Recipient recipient) {
    this.digestRun = digestRun;
    this.recipient = recipient;
    this.status = MailDeliveryStatus.PENDING;
  }

  public Long getId() {
    return id;
  }

  public DigestRun getDigestRun() {
    return digestRun;
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public MailDeliveryStatus getStatus() {
    return status;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  /** Marks the mail as sent at the given time and clears a previous error. */
  public void markSent(Instant sentAt) {
    this.status = MailDeliveryStatus.SENT;
    this.sentAt = sentAt;
    this.errorMessage = null;
  }

  /** Marks the delivery as failed with the given (sanitised) error message. */
  public void markFailed(String errorMessage) {
    this.status = MailDeliveryStatus.FAILED;
    this.errorMessage = errorMessage;
  }
}
