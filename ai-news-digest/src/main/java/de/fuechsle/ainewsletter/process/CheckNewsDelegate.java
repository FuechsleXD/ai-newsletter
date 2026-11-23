package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.research.NewsItemChecker;
import de.fuechsle.ainewsletter.service.DigestRunService;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * "Meldungen prüfen": runs after the join, checks the news items of the run (period, duplicates in
 * the run; duplicates of earlier runs and reachability follow in roadmap step 14) and decides the
 * further path from the stored results.
 */
@Component("checkNewsDelegate")
public class CheckNewsDelegate implements JavaDelegate {

  private final NewsItemChecker checker;
  private final DigestRunService runs;

  public CheckNewsDelegate(NewsItemChecker checker, DigestRunService runs) {
    this.checker = checker;
    this.runs = runs;
  }

  @Override
  public void execute(DelegateExecution execution) {
    long runId = ((Number) execution.getVariable(DigestRunVariables.RUN_ID)).longValue();
    checker.check(runId);
    execution.setVariable(DigestRunVariables.RESEARCH_OUTCOME, runs.researchOutcome(runId).name());
  }
}
