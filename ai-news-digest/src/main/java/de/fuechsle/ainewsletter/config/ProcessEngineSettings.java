package de.fuechsle.ainewsletter.config;

import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.cibseven.bpm.spring.boot.starter.configuration.CamundaProcessEngineConfiguration;
import org.springframework.stereotype.Component;

/**
 * Applies the process defaults from {@link NewsDigestProperties} to the embedded CIB seven engine,
 * so they are defined in one place (docs/12-defaults.md). Static engine settings (schema, table
 * prefix, no schema update) are in application.yml.
 */
@Component
public class ProcessEngineSettings implements CamundaProcessEngineConfiguration {

  private final NewsDigestProperties.Process process;

  public ProcessEngineSettings(NewsDigestProperties properties) {
    this.process = properties.process();
  }

  @Override
  public void preInit(ProcessEngineConfigurationImpl configuration) {
    configuration.setHistoryTimeToLive("P" + process.historyTimeToLive().toDays() + "D");
    configuration.setFailedJobRetryTimeCycle(process.externalTaskRetryCycle());
  }

  @Override
  public void postInit(ProcessEngineConfigurationImpl configuration) {
    configuration.getJobExecutor().setLockTimeInMillis((int) process.jobLockTime().toMillis());
  }
}
