package de.fuechsle.ainewsletter.mail;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.MailDelivery;
import de.fuechsle.ainewsletter.domain.MailDeliveryStatus;
import de.fuechsle.ainewsletter.domain.Recipient;
import de.fuechsle.ainewsletter.render.DigestLanguage;
import de.fuechsle.ainewsletter.render.DigestRenderer;
import de.fuechsle.ainewsletter.render.DigestView;
import de.fuechsle.ainewsletter.render.RenderDigestService;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.MailDeliveryRepository;
import de.fuechsle.ainewsletter.repository.RecipientRepository;
import de.fuechsle.ainewsletter.storage.DigestStorage;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the digest of a run to the active recipients of its topic and logs every delivery in {@code
 * mail_delivery} (docs/04-prozess-bpmn.md, "Fehlerverhalten" and "Idempotenz").
 *
 * <ul>
 *   <li>A recipient whose delivery is {@code SENT} never gets the mail again
 *   <li>Every delivery is stored in its own transaction right after the attempt, so a retried job
 *       knows who already got the mail
 *   <li>If a recipient fails and the job has attempts left, {@link MailDeliveryIncompleteException}
 *       lets the engine retry the job
 *   <li>In the last attempt the remaining recipients become {@code FAILED}; the run goes on if at
 *       least one recipient got the mail ({@link MailOutcome#PARTIALLY_SENT}), otherwise the
 *       exception leads to an incident
 * </ul>
 *
 * Mail addresses are never logged (golden rule 9); logs only contain recipient IDs.
 */
@Service
public class DigestMailService {

  private static final Logger log = LoggerFactory.getLogger(DigestMailService.class);
  private static final int MAX_ERROR_LENGTH = 1000;

  private final DigestRunRepository runs;
  private final RecipientRepository recipients;
  private final MailDeliveryRepository deliveries;
  private final RenderDigestService renderService;
  private final DigestRenderer renderer;
  private final DigestStorage storage;
  private final MailService mailService;
  private final String subjectPattern;
  private final DigestLanguage language;
  private final Clock clock;
  private final TransactionTemplate newTransaction;

  public DigestMailService(
      DigestRunRepository runs,
      RecipientRepository recipients,
      MailDeliveryRepository deliveries,
      RenderDigestService renderService,
      DigestRenderer renderer,
      DigestStorage storage,
      MailService mailService,
      NewsDigestProperties properties,
      DigestLanguage language,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.runs = runs;
    this.recipients = recipients;
    this.deliveries = deliveries;
    this.renderService = renderService;
    this.renderer = renderer;
    this.storage = storage;
    this.mailService = mailService;
    this.subjectPattern = properties.output().mailSubjectPattern();
    this.language = language;
    this.clock = clock;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Whether the topic of the run has at least one active recipient. */
  public boolean hasRecipients(long runId) {
    return Boolean.TRUE.equals(
        newTransaction.execute(status -> !activeRecipients(runId).isEmpty()));
  }

  /**
   * Sends the digest to every active recipient that has not got it yet.
   *
   * @param lastAttempt whether the job has no further attempt after this one
   * @throws MailDeliveryIncompleteException if a recipient failed and the job should be retried, or
   *     if in the last attempt no recipient got the mail
   */
  public MailOutcome send(long runId, boolean lastAttempt) {
    MailContent content = content(runId);
    List<Addressee> open = newTransaction.execute(status -> openAddressees(runId));

    int failed = 0;
    for (Addressee addressee : open) {
      try {
        mailService.sendDigest(
            addressee.email(), content.subject(), content.html(), content.text(), content.pdf());
        record(runId, addressee.recipientId(), null);
      } catch (MailException e) {
        log.warn("Digest mail of run {} to recipient {} failed", runId, addressee.recipientId());
        record(runId, addressee.recipientId(), errorMessage(e));
        failed++;
      }
    }

    if (failed == 0) {
      return MailOutcome.ALL_SENT;
    }
    if (!lastAttempt) {
      throw new MailDeliveryIncompleteException(runId, failed);
    }
    long sent = newTransaction.execute(status -> markOpenAsFailed(runId));
    if (sent == 0) {
      throw new MailDeliveryIncompleteException(runId, failed);
    }
    return MailOutcome.PARTIALLY_SENT;
  }

  /** Subject from the configured pattern, with the topic and the end of the run period. */
  String subject(DigestRun run) {
    return subjectPattern
        .replace("{topic}", run.getTopic().getName())
        .replace("{date}", language.date(run.getPeriodTo()));
  }

  private MailContent content(long runId) {
    RunMailData data = newTransaction.execute(status -> runMailData(runId));
    DigestView view = renderService.view(runId);
    return new MailContent(
        data.subject(),
        renderer.mailHtml(view),
        renderer.mailText(view),
        storage.load(data.pdfKey()));
  }

  private RunMailData runMailData(long runId) {
    DigestRun run = runs.findWithTopic(runId).orElseThrow();
    if (run.getPdfS3Key() == null) {
      throw new IllegalStateException("Run " + runId + " has no rendered PDF");
    }
    return new RunMailData(subject(run), run.getPdfS3Key());
  }

  private List<Addressee> openAddressees(long runId) {
    return activeRecipients(runId).stream()
        .filter(recipient ->
            deliveries
                .findByDigestRunIdAndRecipientId(runId, recipient.getId())
                .map(delivery -> delivery.getStatus() != MailDeliveryStatus.SENT)
                .orElse(true))
        .map(recipient -> new Addressee(recipient.getId(), recipient.getEmail()))
        .toList();
  }

  private List<Recipient> activeRecipients(long runId) {
    DigestRun run = runs.findById(runId).orElseThrow();
    return recipients.findActiveByTopic(run.getTopic().getId());
  }

  /** Stores the result of one attempt; {@code error == null} means sent. */
  private void record(long runId, long recipientId, String error) {
    newTransaction.executeWithoutResult(status -> {
      MailDelivery delivery =
          deliveries
              .findByDigestRunIdAndRecipientId(runId, recipientId)
              .orElseGet(() ->
                  new MailDelivery(
                      runs.getReferenceById(runId), recipients.getReferenceById(recipientId)));
      if (error == null) {
        delivery.markSent(clock.instant());
      } else {
        delivery.recordFailedAttempt(error);
      }
      deliveries.save(delivery);
    });
  }

  /**
   * Marks the deliveries of the run that are not sent as failed.
   *
   * @return number of recipients that got the mail
   */
  private long markOpenAsFailed(long runId) {
    long sent = 0;
    for (Recipient recipient : activeRecipients(runId)) {
      MailDelivery delivery =
          deliveries.findByDigestRunIdAndRecipientId(runId, recipient.getId()).orElse(null);
      if (delivery == null) {
        continue;
      }
      if (delivery.getStatus() == MailDeliveryStatus.SENT) {
        sent++;
      } else {
        delivery.markFailed(delivery.getErrorMessage());
      }
    }
    return sent;
  }

  private static String errorMessage(MailException e) {
    String message = e.getMostSpecificCause().getMessage();
    if (message == null) {
      message = e.getClass().getSimpleName();
    }
    return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
  }

  private record RunMailData(String subject, String pdfKey) {}

  private record MailContent(String subject, String html, String text, byte[] pdf) {}

  private record Addressee(long recipientId, String email) {}
}
