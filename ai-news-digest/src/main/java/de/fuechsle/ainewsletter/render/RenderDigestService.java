package de.fuechsle.ainewsletter.render;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate;
import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.storage.DigestStorage;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders the digest of a run: builds the {@link DigestView} from {@code digest_run.aggregate_json}
 * and creates the one-page PDF ({@link OnePagePdfRenderer}, which shortens the texts if needed).
 * The PDF is stored under a deterministic key, so a repeated job replaces it. The view depends only
 * on stored data (creation date = start of the run in the time zone of its schedule), so PDF and
 * mail always show the same content; the mail always uses the configured length limits.
 */
@Service
public class RenderDigestService {

  private final DigestRunRepository runs;
  private final ObjectMapper objectMapper;
  private final DigestViewFactory viewFactory;
  private final OnePagePdfRenderer pdfRenderer;
  private final DigestStorage storage;
  private final TransactionTemplate newTransaction;

  public RenderDigestService(
      DigestRunRepository runs,
      ObjectMapper objectMapper,
      DigestViewFactory viewFactory,
      OnePagePdfRenderer pdfRenderer,
      DigestStorage storage,
      PlatformTransactionManager transactionManager) {
    this.runs = runs;
    this.objectMapper = objectMapper;
    this.viewFactory = viewFactory;
    this.pdfRenderer = pdfRenderer;
    this.storage = storage;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** The digest of the run as shown in the mail (and in the PDF, if it fits on one page). */
  public DigestView view(long runId) {
    return view(runId, 1.0);
  }

  /**
   * Renders the PDF, stores it and records its key at the run.
   *
   * @return the storage key
   */
  public String renderPdf(long runId) {
    byte[] pdf = pdfRenderer.render(lengthFactor -> view(runId, lengthFactor));
    String key = DigestStorage.pdfKey(runId);
    storage.store(key, pdf);
    newTransaction.executeWithoutResult(
        status -> runs.findById(runId).orElseThrow().setPdfS3Key(key));
    return key;
  }

  private DigestView view(long runId, double lengthFactor) {
    return newTransaction.execute(
        status -> view(runs.findWithTopic(runId).orElseThrow(), lengthFactor));
  }

  private DigestView view(DigestRun run, double lengthFactor) {
    if (run.getAggregateJson() == null) {
      throw new IllegalStateException("Run " + run.getId() + " has no aggregate");
    }
    DigestAggregate aggregate =
        objectMapper.readValue(run.getAggregateJson(), DigestAggregate.class);
    ZoneId zone = ZoneId.of(run.getTopic().getSchedule().getTimezone());
    return viewFactory.create(
        aggregate,
        run.getTopic().getName(),
        run.getPeriodFrom(),
        run.getPeriodTo(),
        LocalDate.ofInstant(run.getStartedAt(), zone),
        lengthFactor);
  }
}
