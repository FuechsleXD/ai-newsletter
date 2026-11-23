package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.mail.OperatorNotificationReason;
import de.fuechsle.ainewsletter.mail.OperatorNotificationService;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * "Betreiber informieren": mail to the operator; the reason is the input parameter {@code
 * notificationReason} of the task. Mail errors lead to job retries and then to an incident.
 */
@Component("notifyOperatorDelegate")
public class NotifyOperatorDelegate implements JavaDelegate {

  private final OperatorNotificationService notifications;

  public NotifyOperatorDelegate(OperatorNotificationService notifications) {
    this.notifications = notifications;
  }

  @Override
  public void execute(DelegateExecution execution) {
    long runId = ((Number) execution.getVariable(DigestRunVariables.RUN_ID)).longValue();
    String reason = (String) execution.getVariableLocal(DigestRunVariables.NOTIFICATION_REASON);
    notifications.notify(runId, OperatorNotificationReason.valueOf(reason));
  }
}
