package de.fuechsle.ainewsletter.service;

import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.RunStatus;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import java.time.Clock;
import org.cibseven.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels a running digest run: ends its process instance (open jobs and incidents go with it) and
 * sets the run to {@code CANCELLED}. Afterwards the topic can run again, because the partial unique
 * index only covers {@code RUNNING} (docs/04-prozess-bpmn.md).
 */
@Service
public class RunCancellationService {

  private static final Logger log = LoggerFactory.getLogger(RunCancellationService.class);

  private final RuntimeService runtime;
  private final DigestRunRepository runs;
  private final Clock clock;

  public RunCancellationService(RuntimeService runtime, DigestRunRepository runs, Clock clock) {
    this.runtime = runtime;
    this.runs = runs;
    this.clock = clock;
  }

  /**
   * Cancels the run. The run row is locked, so the end of the process cannot overwrite the status;
   * a job that is still executing fails on commit, because its process instance is gone.
   *
   * @param reason stored as error message of the run (German, shown to the operator)
   * @throws RunNotFoundException if the run does not exist
   * @throws RunNotRunningException if the run has already ended
   */
  @Transactional
  public void cancel(long runId, String reason) {
    DigestRun run = runs.findForUpdate(runId).orElseThrow(() -> new RunNotFoundException(runId));
    if (run.getStatus() != RunStatus.RUNNING) {
      throw new RunNotRunningException(runId, run.getStatus());
    }
    if (run.getProcessInstanceId() != null) {
      // Skip custom listeners: the budget warning belongs to runs that ended on their own
      runtime.deleteProcessInstanceIfExists(
          run.getProcessInstanceId(), reason, true, true, true, false);
    }
    run.finish(RunStatus.CANCELLED, clock.instant());
    run.setErrorMessage(reason);
    log.info("Run {} cancelled: {}", runId, reason);
  }
}
