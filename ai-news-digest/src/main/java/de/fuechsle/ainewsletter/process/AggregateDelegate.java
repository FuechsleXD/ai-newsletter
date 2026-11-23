package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.aggregate.AggregationOutcome;
import de.fuechsle.ainewsletter.aggregate.RunAggregationService;
import org.cibseven.bpm.engine.delegate.BpmnError;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * "Aggregieren": condenses the valid news items with the aggregator model of the run. If the model
 * fails or its answer stays invalid after the repair attempt, the delegate throws the BPMN error
 * {@value #AGGREGATION_FAILED}; the process then informs the operator and ends the run as FAILED. A
 * retry by the engine would only pay the same failing call again. Technical errors outside the
 * model call (e.g. the database) are exceptions and lead to job retries.
 */
@Component("aggregateDelegate")
public class AggregateDelegate implements JavaDelegate {

  /** Error code of the boundary event at the task "Aggregieren". */
  public static final String AGGREGATION_FAILED = "AGGREGATION_FAILED";

  private final RunAggregationService aggregation;

  public AggregateDelegate(RunAggregationService aggregation) {
    this.aggregation = aggregation;
  }

  @Override
  public void execute(DelegateExecution execution) {
    long runId = ((Number) execution.getVariable(DigestRunVariables.RUN_ID)).longValue();
    AggregationOutcome outcome = aggregation.aggregate(runId);
    if (!outcome.succeeded()) {
      throw new BpmnError(AGGREGATION_FAILED, outcome.errorMessage());
    }
  }
}
