package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.mail.DigestMailService;
import de.fuechsle.ainewsletter.mail.MailOutcome;
import java.util.List;
import org.cibseven.bpm.engine.ManagementService;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.cibseven.bpm.engine.runtime.Job;
import org.springframework.stereotype.Component;

/**
 * "Mails versenden": sends the digest to the recipients that have not got it yet and sets {@code
 * mailOutcome} for the following gateway. Whether this is the last attempt of the job is read from
 * its remaining retries: with 1 retry left, a failure would create an incident. This assumes a
 * retry cycle with at least two repetitions ({@code newsdigest.process.external-task-retry-cycle},
 * default {@code R3/PT5M}).
 */
@Component("sendMailsDelegate")
public class SendMailsDelegate implements JavaDelegate {

  private final DigestMailService mail;
  private final ManagementService managementService;

  public SendMailsDelegate(DigestMailService mail, ManagementService managementService) {
    this.mail = mail;
    this.managementService = managementService;
  }

  @Override
  public void execute(DelegateExecution execution) {
    long runId = ((Number) execution.getVariable(DigestRunVariables.RUN_ID)).longValue();
    MailOutcome outcome = mail.send(runId, isLastAttempt(execution));
    execution.setVariable(DigestRunVariables.MAIL_OUTCOME, outcome.name());
  }

  /** Without a job (synchronous execution) there is no retry either. */
  private boolean isLastAttempt(DelegateExecution execution) {
    List<Job> jobs =
        managementService
            .createJobQuery()
            .executionId(execution.getId())
            .activityId(execution.getCurrentActivityId())
            .list();
    return jobs.isEmpty() || jobs.getFirst().getRetries() <= 1;
  }
}
