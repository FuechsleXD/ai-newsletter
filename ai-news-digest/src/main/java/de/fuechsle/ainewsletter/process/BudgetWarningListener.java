package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.cost.BudgetWarningService;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

/**
 * End listener of the process {@code digestRun}: after every run, whatever its end, the costs are
 * recorded, so this is where the budget warning is checked (docs/04-prozess-bpmn.md). Never throws,
 * so it cannot fail or repeat the last job of the run.
 */
@Component("budgetWarningListener")
public class BudgetWarningListener implements ExecutionListener {

  private final BudgetWarningService warnings;

  public BudgetWarningListener(BudgetWarningService warnings) {
    this.warnings = warnings;
  }

  @Override
  public void notify(DelegateExecution execution) {
    warnings.warnIfNeeded();
  }
}
