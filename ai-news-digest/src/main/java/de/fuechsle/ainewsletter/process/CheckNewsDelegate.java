package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.service.DigestRunService;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * "Meldungen prüfen": runs after the join and decides the further path from the stored results. The
 * checks of the news items (period, duplicates, reachability) follow in roadmap steps 7 and 14.
 */
@Component("checkNewsDelegate")
public class CheckNewsDelegate implements JavaDelegate {

  private final DigestRunService runs;

  public CheckNewsDelegate(DigestRunService runs) {
    this.runs = runs;
  }

  @Override
  public void execute(DelegateExecution execution) {
    long runId = ((Number) execution.getVariable(DigestRunVariables.RUN_ID)).longValue();
    execution.setVariable(DigestRunVariables.RESEARCH_OUTCOME, runs.researchOutcome(runId).name());
  }
}
