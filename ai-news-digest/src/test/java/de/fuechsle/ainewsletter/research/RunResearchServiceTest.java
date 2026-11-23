package de.fuechsle.ainewsletter.research;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Storing research results, including a repeated research of the same run and model. */
class RunResearchServiceTest extends ProcessTestSupport {

  @Autowired private RunResearchService service;

  @Test
  void repeatedResearchKeepsOneRowReplacesItemsAndAddsTheCost() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long runId = finishedRun(topic("Wiederholung " + suffix, model, model), model);

    long first = service.research(runId, model);
    long second = service.research(runId, model);

    assertThat(second).isEqualTo(first);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT count(*) OVER () AS rows, input_tokens, output_tokens, search_calls,"
                + " cost_usd, duration_ms FROM provider_result WHERE digest_run_id = ?",
            runId);
    assertThat(row.get("rows")).isEqualTo(1L);
    // Both calls were billed: 2 x (1,000 in, 500 out, 1 search)
    assertThat(row.get("input_tokens")).isEqualTo(2_000);
    assertThat(row.get("output_tokens")).isEqualTo(1_000);
    assertThat(row.get("search_calls")).isEqualTo(2);
    assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.0410");
    assertThat((Long) row.get("duration_ms")).isEqualTo(20L);
    // News items of the first call are replaced, not duplicated
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM news_item WHERE provider_result_id = ?", Long.class, first))
        .isEqualTo(2L);
  }

  @Test
  void failedResearchIsStoredAsFailedRowWithoutItems() {
    String suffix = unique();
    long model = model("fail-" + suffix);
    long runId = finishedRun(topic("Fehler " + suffix, model, model), model);

    long id = service.research(runId, model);

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT status, error_message, cost_usd FROM provider_result WHERE id = ?", id);
    assertThat(row.get("status")).isEqualTo("FAILED");
    assertThat((String) row.get("error_message")).contains("Fake provider error");
    assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM news_item WHERE provider_result_id = ?", Long.class, id))
        .isZero();
  }

  /** A finished run, so the partial unique index for running runs does not interfere. */
  private long finishedRun(long topicId, long aggregatorId) {
    return jdbc.queryForObject(
        "INSERT INTO digest_run (topic_id, aggregator_model_id, prompt_version, period_from,"
            + " period_to, status) VALUES (?, ?, 'v1', DATE '2026-11-16', DATE '2026-11-22',"
            + " 'COMPLETED') RETURNING id",
        Long.class,
        topicId,
        aggregatorId);
  }
}
