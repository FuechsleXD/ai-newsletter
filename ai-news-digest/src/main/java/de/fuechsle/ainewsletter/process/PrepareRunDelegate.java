package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.service.DigestRunService;
import de.fuechsle.ainewsletter.service.PreparedRun;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/** "Lauf vorbereiten": creates the run and passes period and models to the process. */
@Component("prepareRunDelegate")
public class PrepareRunDelegate implements JavaDelegate {

  private final DigestRunService runs;

  public PrepareRunDelegate(DigestRunService runs) {
    this.runs = runs;
  }

  @Override
  public void execute(DelegateExecution execution) {
    long topicId = ((Number) execution.getVariable(DigestRunVariables.TOPIC_ID)).longValue();
    PreparedRun run = runs.prepare(topicId, execution.getProcessInstanceId());

    execution.setVariable(DigestRunVariables.RUN_ID, run.runId());
    execution.setVariable(DigestRunVariables.PERIOD_FROM, run.period().from().toString());
    execution.setVariable(DigestRunVariables.PERIOD_TO, run.period().to().toString());
    execution.setVariable(
        DigestRunVariables.MODELS, DigestRunVariables.encodeModelIds(run.modelIds()));
    execution.setVariable(DigestRunVariables.AGGREGATOR_MODEL_ID, run.aggregatorModelId());
  }
}
