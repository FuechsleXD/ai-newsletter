package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.mail.DigestMailService;
import de.fuechsle.ainewsletter.render.RenderDigestService;
import org.cibseven.bpm.engine.delegate.BpmnError;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * "HTML und PDF erzeugen": renders the PDF of the run and stores it. A topic without active
 * recipients ends with the BPMN error {@value #NO_RECIPIENTS} before rendering; the process then
 * informs the operator and ends the run as FAILED. Gotenberg errors are exceptions and lead to job
 * retries, then to an incident.
 */
@Component("renderDigestDelegate")
public class RenderDigestDelegate implements JavaDelegate {

  /** Error code of the boundary event at the task "HTML und PDF erzeugen". */
  public static final String NO_RECIPIENTS = "NO_RECIPIENTS";

  private final DigestMailService mail;
  private final RenderDigestService render;

  public RenderDigestDelegate(DigestMailService mail, RenderDigestService render) {
    this.mail = mail;
    this.render = render;
  }

  @Override
  public void execute(DelegateExecution execution) {
    long runId = ((Number) execution.getVariable(DigestRunVariables.RUN_ID)).longValue();
    if (!mail.hasRecipients(runId)) {
      throw new BpmnError(NO_RECIPIENTS, "Topic of run " + runId + " has no active recipients");
    }
    render.renderPdf(runId);
  }
}
