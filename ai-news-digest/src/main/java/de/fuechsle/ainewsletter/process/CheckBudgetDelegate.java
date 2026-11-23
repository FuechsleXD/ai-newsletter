package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.cost.BudgetService;
import de.fuechsle.ainewsletter.cost.BudgetStatus;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** "Budget prüfen": no model is called once the monthly budget is reached (golden rule 5). */
@Component("checkBudgetDelegate")
public class CheckBudgetDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(CheckBudgetDelegate.class);

  private final BudgetService budget;

  public CheckBudgetDelegate(BudgetService budget) {
    this.budget = budget;
  }

  @Override
  public void execute(DelegateExecution execution) {
    BudgetStatus status = budget.check();
    if (status.exhausted()) {
      log.warn(
          "Monthly budget reached ({} of {} USD), run {} is skipped",
          status.spentUsd(),
          status.limitUsd(),
          execution.getVariable(DigestRunVariables.RUN_ID));
    }
    execution.setVariable(DigestRunVariables.BUDGET_OK, !status.exhausted());
  }
}
