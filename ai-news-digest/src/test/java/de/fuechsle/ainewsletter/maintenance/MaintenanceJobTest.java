package de.fuechsle.ainewsletter.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fuechsle.ainewsletter.support.TestProperties;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MaintenanceJobTest {

  private final OverdueRunCanceller overdue = mock(OverdueRunCanceller.class);
  private final RetentionService retention = mock(RetentionService.class);

  @Test
  void failingTaskDoesNotStopTheOthers() {
    when(overdue.cancelOverdue()).thenThrow(new IllegalStateException("db down"));
    when(retention.cleanUp()).thenThrow(new IllegalStateException("db down"));
    MaintenanceJob job =
        new MaintenanceJob(overdue, retention, Clock.systemUTC(), TestProperties.defaults());
    try {
      job.run();
    } finally {
      job.stop();
    }

    verify(retention).cleanUp();
    verify(retention).startHistoryCleanup();
  }

  @Test
  void reasonNamesTheMaximumDuration() {
    assertThat(OverdueRunCanceller.reason(Duration.ofHours(3)))
        .isEqualTo("Automatisch abgebrochen: Höchstdauer von 3 h überschritten");
    assertThat(OverdueRunCanceller.reason(Duration.ofMinutes(90))).endsWith("90 min überschritten");
  }
}
