package de.fuechsle.ainewsletter.process;

import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Placeholders for tasks whose implementation follows in later roadmap steps. They only log, so the
 * complete process already runs end to end. Each one is replaced by a real delegate with the same
 * bean name; the BPMN stays unchanged.
 */
@Configuration(proxyBeanMethods = false)
public class PendingStepDelegates {

  private static final Logger log = LoggerFactory.getLogger(PendingStepDelegates.class);

  @Bean
  JavaDelegate aggregateDelegate() {
    return pending("Aggregieren", 7);
  }

  @Bean
  JavaDelegate renderDigestDelegate() {
    return pending("HTML und PDF erzeugen", 8);
  }

  @Bean
  JavaDelegate sendMailsDelegate() {
    return pending("Mails versenden", 8);
  }

  @Bean
  JavaDelegate notifyOperatorDelegate() {
    return pending("Betreiber informieren", 8);
  }

  static JavaDelegate pending(String task, int roadmapStep) {
    return (DelegateExecution execution) ->
        log.info(
            "Task '{}' of run {} is not implemented yet (roadmap step {}), skipped",
            task,
            execution.getVariable(DigestRunVariables.RUN_ID),
            roadmapStep);
  }
}
