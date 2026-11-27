package de.fuechsle.ainewsletter.cost;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cost report over all model calls (research and aggregation) grouped by topic, model or month
 * (docs/05-api.md, {@code GET /costs}, endpoint in roadmap step 19). A run belongs to the day and
 * month of its start in Europe/Berlin, like the monthly budget; the aggregation counts for the
 * aggregator model. Estimated costs (ADR-014) are included and also shown on their own.
 */
@Service
public class CostReportService {

  /** What the costs are grouped by. */
  public enum Grouping {
    TOPIC("t.name"),
    MODEL("m.display_name"),
    MONTH("to_char(c.started_at AT TIME ZONE 'Europe/Berlin', 'YYYY-MM')");

    private final String key;

    Grouping(String key) {
      this.key = key;
    }
  }

  /**
   * One group of the report.
   *
   * @param key topic name, model display name or month ({@code YYYY-MM})
   * @param estimatedUsd part of {@code costUsd} that is an estimate
   * @param calls research results and aggregations
   */
  public record CostRow(
      String key,
      BigDecimal costUsd,
      BigDecimal estimatedUsd,
      long runs,
      long calls,
      long inputTokens,
      long outputTokens,
      long webSearches) {}

  private static final String CALLS =
      """
      WITH calls AS (
        SELECT r.id AS run_id, r.topic_id, p.model_id, p.cost_usd, p.cost_estimated,
               coalesce(p.input_tokens, 0) AS input_tokens,
               coalesce(p.output_tokens, 0) AS output_tokens,
               coalesce(p.search_calls, 0) AS web_searches, r.started_at
        FROM provider_result p JOIN digest_run r ON r.id = p.digest_run_id
        WHERE p.cost_usd IS NOT NULL
        UNION ALL
        SELECT r.id, r.topic_id, a.model_id, a.cost_usd, a.cost_estimated, a.input_tokens,
               a.output_tokens, 0, r.started_at
        FROM aggregation_result a JOIN digest_run r ON r.id = a.digest_run_id
      )
      """;

  private final JdbcTemplate jdbc;

  public CostReportService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Costs of the runs started from {@code from} to {@code to} (both inclusive, Europe/Berlin), most
   * expensive group first.
   */
  @Transactional(readOnly = true)
  public List<CostRow> costs(LocalDate from, LocalDate to, Grouping grouping) {
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to must not be before from");
    }
    String sql =
        CALLS
            + "SELECT "
            + grouping.key
            + " AS key, sum(c.cost_usd) AS cost_usd,"
            + " coalesce(sum(c.cost_usd) FILTER (WHERE c.cost_estimated), 0) AS estimated_usd,"
            + " count(DISTINCT c.run_id) AS runs, count(*) AS calls,"
            + " sum(c.input_tokens) AS input_tokens, sum(c.output_tokens) AS output_tokens,"
            + " sum(c.web_searches) AS web_searches"
            + " FROM calls c JOIN topic t ON t.id = c.topic_id JOIN ai_model m ON m.id = c.model_id"
            + " WHERE c.started_at >= ? AND c.started_at < ?"
            + " GROUP BY 1 ORDER BY 2 DESC, 1";
    return jdbc.query(
        sql,
        (rs, row) ->
            new CostRow(
                rs.getString("key"),
                rs.getBigDecimal("cost_usd").setScale(CostCalculator.SCALE),
                rs.getBigDecimal("estimated_usd").setScale(CostCalculator.SCALE),
                rs.getLong("runs"),
                rs.getLong("calls"),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                rs.getLong("web_searches")),
        start(from),
        start(to.plusDays(1)));
  }

  private static Timestamp start(LocalDate day) {
    return Timestamp.from(day.atStartOfDay(BudgetService.BUDGET_ZONE).toInstant());
  }
}
