package de.fuechsle.ainewsletter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RunPeriodCalculatorTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  // Monday 2026-11-23 06:30 UTC = 07:30 in Berlin
  private final RunPeriodCalculator calculator =
      new RunPeriodCalculator(Clock.fixed(Instant.parse("2026-11-23T06:30:00Z"), ZoneOffset.UTC));

  @Test
  void firstRunLooksBackTheConfiguredDays() {
    assertThat(calculator.calculate(BERLIN, null, 7))
        .isEqualTo(new RunPeriod(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23)));
  }

  @Test
  void nextRunStartsTheDayAfterTheLastSuccessfulRun() {
    assertThat(calculator.calculate(BERLIN, LocalDate.of(2026, 11, 16), 7))
        .isEqualTo(new RunPeriod(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 23)));
  }

  @Test
  void runOnTheSameDayCoversTodayOnly() {
    assertThat(calculator.calculate(BERLIN, LocalDate.of(2026, 11, 23), 7))
        .isEqualTo(new RunPeriod(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 23)));
  }

  @Test
  void todayIsDeterminedInTheTimeZoneOfTheSchedule() {
    // 2026-11-23 23:30 UTC is already 2026-11-24 in Berlin, but still 2026-11-23 in New York
    RunPeriodCalculator lateEvening =
        new RunPeriodCalculator(Clock.fixed(Instant.parse("2026-11-23T23:30:00Z"), ZoneOffset.UTC));

    assertThat(lateEvening.calculate(BERLIN, null, 1).to()).isEqualTo(LocalDate.of(2026, 11, 24));
    assertThat(lateEvening.calculate(ZoneId.of("America/New_York"), null, 1).to())
        .isEqualTo(LocalDate.of(2026, 11, 23));
  }
}
