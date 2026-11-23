package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.research.RunResearchService;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * "Recherche" for one model (one multi-instance instance). Writes only its own {@code
 * provider_result} row, never shared process variables (ADR-004). Model errors end as a FAILED row;
 * only technical errors before the call lead to a job retry.
 */
@Component("researchDelegate")
public class ResearchDelegate implements JavaDelegate {

  private final RunResearchService research;

  public ResearchDelegate(RunResearchService research) {
    this.research = research;
  }

  @Override
  public void execute(DelegateExecution execution) {
    long runId = ((Number) execution.getVariable(DigestRunVariables.RUN_ID)).longValue();
    long modelId = ((Number) execution.getVariable(DigestRunVariables.MODEL)).longValue();
    research.research(runId, modelId);
  }
}
