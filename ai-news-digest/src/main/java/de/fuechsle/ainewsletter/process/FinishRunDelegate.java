package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.domain.RunStatus;
import de.fuechsle.ainewsletter.service.DigestRunService;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * "Lauf abschließen": sets the final status, which the BPMN passes as input parameter {@code
 * runStatus} of the task.
 */
@Component("finishRunDelegate")
public class FinishRunDelegate implements JavaDelegate {

  private final DigestRunService runs;

  public FinishRunDelegate(DigestRunService runs) {
    this.runs = runs;
  }

  @Override
  public void execute(DelegateExecution execution) {
    long runId = ((Number) execution.getVariable(DigestRunVariables.RUN_ID)).longValue();
    RunStatus status =
        RunStatus.valueOf((String) execution.getVariableLocal(DigestRunVariables.RUN_STATUS));
    runs.finish(runId, status);
  }
}
