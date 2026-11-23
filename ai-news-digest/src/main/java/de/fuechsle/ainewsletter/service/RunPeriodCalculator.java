package de.fuechsle.ainewsletter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Calculates the period of a run (docs/02-architektur.md, "Zeitzonen"): it ends today in the time
 * zone of the schedule and starts the day after the last successful run. The first run looks back
 * the configured number of days. If the last successful run already covered today, the period is
 * today only.
 */
@Component
public class RunPeriodCalculator {

  private final Clock clock;

  public RunPeriodCalculator(Clock clock) {
    this.clock = clock;
  }

  /**
   * @param lastSuccessfulRunAt {@code period_to} of the last run with status COMPLETED or NO_NEWS,
   *     {@code null} before the first such run
   * @param initialLookbackDays days to look back on the first run
   */
  public RunPeriod calculate(ZoneId zone, LocalDate lastSuccessfulRunAt, int initialLookbackDays) {
    LocalDate today = LocalDate.now(clock.withZone(zone));
    if (lastSuccessfulRunAt == null) {
      return new RunPeriod(today.minusDays(initialLookbackDays), today);
    }
    LocalDate from = lastSuccessfulRunAt.plusDays(1);
    return new RunPeriod(from.isAfter(today) ? today : from, today);
  }
}
