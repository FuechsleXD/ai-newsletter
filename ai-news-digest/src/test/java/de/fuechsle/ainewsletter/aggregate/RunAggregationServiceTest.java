package de.fuechsle.ainewsletter.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.research.NewsItemChecker;
import de.fuechsle.ainewsletter.research.RunResearchService;
import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Check and aggregation of a run with a real database and the fake provider, without the engine:
 * duplicates within the run, consensus of several models, repeated and failed aggregation.
 */
class RunAggregationServiceTest extends ProcessTestSupport {

  @Autowired private RunResearchService research;
  @Autowired private NewsItemChecker checker;
  @Autowired private RunAggregationService service;

  @Test
  void sameUrlOfTwoModelsIsOneItemWithBothModelsInTheConsensus() {
    String suffix = unique();
    long first = model("ok-a-" + suffix);
    long second = model("ok-b-" + suffix);
    long runId = run(topic("Konsens " + suffix, first, first, second), first);
    research.research(runId, first);
    research.research(runId, second);

    checker.check(runId);
    AggregationOutcome outcome = service.aggregate(runId);

    assertThat(statusCounts(runId)).containsEntry("VALID", 2L).containsEntry("DUPLICATE_RUN", 2L);
    // Trailing slash removed for the duplicate check, original URL kept
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT normalized_url FROM news_item n JOIN provider_result p"
                    + " ON p.id = n.provider_result_id WHERE p.digest_run_id = ? AND url LIKE '%/'",
                String.class, runId))
        .containsExactly("https://jdk.example.test/ea");

    assertThat(outcome.succeeded()).isTrue();
    JsonNode aggregate = aggregateJson(runId);
    assertThat(aggregate.get("title").asString()).isEqualTo("Fake-Digest");
    assertThat(aggregate.get("sections")).hasSize(2);
    for (JsonNode section : aggregate.get("sections")) {
      assertThat(texts(section.get("models"))).containsExactly("ok-a-" + suffix, "ok-b-" + suffix);
      assertThat(section.get("iconKey").asString()).isEqualTo(IconKeys.DEFAULT);
    }
    assertThat(aggregate.get("sources")).hasSize(2);
    assertThat(aggregate.get("sources").get(0).get("url").asString()).startsWith("https://");
  }

  @Test
  void repeatedAggregationKeepsOneRowReplacesTheContentAndAddsTheCost() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long runId = run(topic("Wiederholt " + suffix, model, model), model);
    research.research(runId, model);
    checker.check(runId);

    service.aggregate(runId);
    service.aggregate(runId);

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT count(*) OVER () AS rows, model_id, input_tokens, output_tokens, cost_usd,"
                + " duration_ms FROM aggregation_result WHERE digest_run_id = ?",
            runId);
    assertThat(row.get("rows")).isEqualTo(1L);
    assertThat(row.get("model_id")).isEqualTo(model);
    assertThat(row.get("input_tokens")).isEqualTo(2_000);
    assertThat(row.get("output_tokens")).isEqualTo(1_000);
    // 2 x (1,000 * 3 / 1M + 500 * 15 / 1M)
    assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.021");
    assertThat((Long) row.get("duration_ms")).isEqualTo(20L);
    assertThat(aggregateJson(runId)).isNotNull();
  }

  @Test
  void itemOutsideThePeriodIsNotGivenToTheAggregator() {
    String suffix = unique();
    long model = model("old-" + suffix);
    long runId = run(topic("Alt " + suffix, model, model), model);
    research.research(runId, model);

    checker.check(runId);
    service.aggregate(runId);

    assertThat(statusCounts(runId)).containsEntry("OUT_OF_PERIOD", 1L).containsEntry("VALID", 1L);
    JsonNode sources = aggregateJson(runId).get("sources");
    assertThat(sources).hasSize(1);
    assertThat(sources.get(0).get("url").asString()).isEqualTo("https://jdk.example.test/ea/");
  }

  @Test
  void invalidAnswerAfterRepairFailsWithCostAndReasonAtTheRun() {
    String suffix = unique();
    long researcher = model("ok-" + suffix);
    long aggregator = model("garbage-" + suffix);
    long runId = run(topic("Kaputt " + suffix, aggregator, researcher), aggregator);
    research.research(runId, researcher);
    checker.check(runId);

    AggregationOutcome outcome = service.aggregate(runId);

    assertThat(outcome.succeeded()).isFalse();
    assertThat(fakeProvider.aggregations("garbage-" + suffix)).isEqualTo(2);
    Map<String, Object> run =
        jdbc.queryForMap(
            "SELECT aggregate_json, error_message FROM digest_run WHERE id = ?", runId);
    assertThat(run.get("aggregate_json")).isNull();
    assertThat((String) run.get("error_message"))
        .startsWith("Aggregation fehlgeschlagen: Antwort ist kein gültiges JSON");
    assertThat(
            jdbc.queryForObject(
                "SELECT cost_usd FROM aggregation_result WHERE digest_run_id = ?",
                BigDecimal.class,
                runId))
        .isEqualByComparingTo("0.021");
  }

  /** A finished run over the last week, so the fake items dated today are in the period. */
  private long run(long topicId, long aggregatorId) {
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
    return jdbc.queryForObject(
        "INSERT INTO digest_run (topic_id, aggregator_model_id, prompt_version, period_from,"
            + " period_to, status) VALUES (?, ?, 'v1', ?, ?, 'COMPLETED') RETURNING id",
        Long.class,
        topicId,
        aggregatorId,
        today.minusDays(7),
        today);
  }

  private Map<String, Long> statusCounts(long runId) {
    return jdbc
        .queryForList(
            "SELECT n.status, count(*) AS items FROM news_item n JOIN provider_result p"
                + " ON p.id = n.provider_result_id WHERE p.digest_run_id = ? GROUP BY n.status",
            runId)
        .stream()
        .collect(
            java.util.stream.Collectors.toMap(
                row -> (String) row.get("status"), row -> (Long) row.get("items")));
  }

  private JsonNode aggregateJson(long runId) {
    String json =
        jdbc.queryForObject(
            "SELECT aggregate_json::text FROM digest_run WHERE id = ?", String.class, runId);
    return json == null ? null : new JsonMapper().readTree(json);
  }

  private static List<String> texts(JsonNode array) {
    return array.valueStream().map(JsonNode::asString).toList();
  }
}
