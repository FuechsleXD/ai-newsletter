package de.fuechsle.ainewsletter.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.maintenance.MaintenanceJob;
import de.fuechsle.ainewsletter.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * The scheduler starts as in profile {@code prod}. Without misfire grace it makes up nothing, so
 * the topics of other tests in the shared database are not started; the job executor is off.
 */
@SpringBootTest(
    properties = {
      "newsdigest.scheduler.enabled=true",
      "newsdigest.scheduler.misfire-grace=0s",
      "newsdigest.maintenance.initial-delay=1h"
    })
// Same level as the switch in PostgresTestSupport, which would win over @SpringBootTest
@TestPropertySource(properties = "newsdigest.maintenance.enabled=true")
class SchedulerWiringTest extends PostgresTestSupport {

  @Autowired private ApplicationContext context;

  @Test
  void schedulerAndMaintenanceJobAreActiveWhenEnabled() {
    assertThat(context.getBeansOfType(DigestScheduler.class)).hasSize(1);
    assertThat(context.getBeansOfType(MaintenanceJob.class)).hasSize(1);
  }
}
