package de.fuechsle.ainewsletter.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.service.RunAlreadyActiveException;
import de.fuechsle.ainewsletter.service.TopicNotFoundException;
import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.cibseven.bpm.engine.history.HistoricActivityInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The process {@code digestRun} with the embedded engine, a real database and the fake provider
 * (mandatory cases from docs/10-tests.md that apply with one model; parallelism follows in step
 * 13).
 */
class DigestRunProcessTest extends ProcessTestSupport {

  @Autowired private DigestProcessStarter starter;

  @Test
  void happyPathCompletesAndMovesTheLastSuccessfulRun() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Happy " + suffix, model, model);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("COMPLETED");
    Map<String, Object> run =
        jdbc.queryForMap(
            "SELECT id, period_from, period_to, prompt_version, finished_at FROM digest_run"
                + " WHERE process_instance_id = ?",
            instance);
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
    assertThat(run.get("period_to").toString()).isEqualTo(today.toString());
    assertThat(run.get("period_from").toString()).isEqualTo(today.minusDays(7).toString());
    assertThat(run.get("prompt_version")).isEqualTo("v1");
    assertThat(run.get("finished_at")).isNotNull();

    Map<String, Object> result =
        jdbc.queryForMap(
            "SELECT status, input_tokens, search_calls, cost_usd FROM provider_result"
                + " WHERE digest_run_id = ?",
            run.get("id"));
    assertThat(result.get("status")).isEqualTo("SUCCEEDED");
    assertThat(result.get("search_calls")).isEqualTo(1);
    // 1,000 * 3 / 1M + 500 * 15 / 1M + 1 * 0.01
    assertThat((BigDecimal) result.get("cost_usd")).isEqualByComparingTo("0.0205");
    assertThat(newsItems((Long) run.get("id"))).isEqualTo(2);

    assertThat(lastSuccessfulRun(topic)).isEqualTo(today);
    assertThat(visited(instance))
        .contains(
            "prepareRun",
            "checkBudget",
            "researchFork",
            "research",
            "researchJoin",
            "checkNews",
            "aggregate",
            "render",
            "sendMails",
            "finishCompleted",
            "endCompleted");
  }

  @Test
  void failingModelEndsTheRunAsFailedWithoutMovingTheLastSuccessfulRun() {
    String suffix = unique();
    long model = model("fail-" + suffix);
    long topic = topic("Fail " + suffix, model, model);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT error_message FROM provider_result WHERE digest_run_id = ?",
                String.class,
                runId(instance)))
        .contains("Fake provider error");
    assertThat(lastSuccessfulRun(topic)).isNull();
    assertThat(visited(instance)).contains("notifyOperatorFailed", "endFailed");
  }

  @Test
  void noValidNewsEndsAsNoNewsAndMovesTheLastSuccessfulRun() {
    String suffix = unique();
    long model = model("empty-" + suffix);
    long topic = topic("Empty " + suffix, model, model);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("NO_NEWS");
    assertThat(lastSuccessfulRun(topic)).isEqualTo(LocalDate.now(ZoneId.of("Europe/Berlin")));
    assertThat(visited(instance)).doesNotContain("aggregate", "sendMails");
  }

  @Test
  void topicWithoutResearchModelsEndsAsFailed() {
    String suffix = unique();
    long aggregator = model("ok-" + suffix);
    long topic = topic("Ohne Modelle " + suffix, aggregator);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("FAILED");
  }

  @Test
  void exhaustedBudgetSkipsTheRunWithoutCallingAModel() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Budget " + suffix, model, model);
    jdbc.update("INSERT INTO budget (month_limit_usd, warn_percent, active) VALUES (0, 80, true)");
    try {
      String instance = starter.start(topic);
      awaitEnd(instance);

      assertThat(runStatus(instance)).isEqualTo("SKIPPED_BUDGET");
      assertThat(fakeProvider.calls("ok-" + suffix)).isZero();
      assertThat(lastSuccessfulRun(topic)).isNull();
      assertThat(visited(instance)).contains("notifyOperatorBudget", "endSkipped");
    } finally {
      jdbc.update("UPDATE budget SET active = false WHERE active");
    }
  }

  @Test
  void secondStartOfARunningTopicIsRejected() {
    String suffix = unique();
    long model = model("slow-" + suffix);
    long topic = topic("Slow " + suffix, model, model);

    String instance = starter.start(topic);
    try {
      fakeProvider.awaitSlowCall();

      assertThatThrownBy(() -> starter.start(topic))
          .isInstanceOf(RunAlreadyActiveException.class)
          .hasMessageContaining(String.valueOf(topic));
    } finally {
      fakeProvider.release();
    }
    awaitEnd(instance);
    assertThat(runStatus(instance)).isEqualTo("COMPLETED");

    // After the run has ended, the topic can run again
    long fast = model("ok-" + suffix);
    jdbc.update("UPDATE topic_model SET model_id = ? WHERE topic_id = ?", fast, topic);
    String next = starter.start(topic);
    awaitEnd(next);
    assertThat(runStatus(next)).isEqualTo("COMPLETED");
  }

  @Test
  void changedModelChoiceAppliesToTheNextRunWithoutNewProcessVersion() {
    String suffix = unique();
    long first = model("ok-a-" + suffix);
    long second = model("ok-b-" + suffix);
    long topic = topic("Wechsel " + suffix, first, first);
    String firstRun = starter.start(topic);
    awaitEnd(firstRun);

    jdbc.update("UPDATE topic_model SET model_id = ? WHERE topic_id = ?", second, topic);
    String secondRun = starter.start(topic);
    awaitEnd(secondRun);

    assertThat(fakeProvider.calls("ok-a-" + suffix)).isEqualTo(1);
    assertThat(fakeProvider.calls("ok-b-" + suffix)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT model_id FROM provider_result WHERE digest_run_id = ?",
                Long.class,
                runId(secondRun)))
        .isEqualTo(second);
    // Both runs use the same deployed process definition
    assertThat(processDefinition(firstRun)).isEqualTo(processDefinition(secondRun));
    // The second run continues where the first one ended (same day: today only)
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
    assertThat(
            jdbc.queryForObject(
                "SELECT period_from FROM digest_run WHERE id = ?",
                LocalDate.class,
                runId(secondRun)))
        .isEqualTo(today);
  }

  @Test
  void inactiveModelsAreNotResearched() {
    String suffix = unique();
    long active = model("ok-active-" + suffix);
    long inactive = model("ok-inactive-" + suffix);
    jdbc.update("UPDATE ai_model SET active = false WHERE id = ?", inactive);
    long topic = topic("Inaktiv " + suffix, active, active, inactive);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(fakeProvider.calls("ok-inactive-" + suffix)).isZero();
    assertThat(fakeProvider.calls("ok-active-" + suffix)).isEqualTo(1);
  }

  @Test
  void unknownTopicCannotBeStarted() {
    assertThatThrownBy(() -> starter.start(Long.MAX_VALUE))
        .isInstanceOf(TopicNotFoundException.class);
  }

  private LocalDate lastSuccessfulRun(long topicId) {
    List<LocalDate> values =
        jdbc.queryForList(
            "SELECT last_successful_run_at FROM topic_state WHERE topic_id = ?",
            LocalDate.class,
            topicId);
    return values.isEmpty() ? null : values.getFirst();
  }

  private long newsItems(long runId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM news_item n JOIN provider_result p ON p.id = n.provider_result_id"
            + " WHERE p.digest_run_id = ?",
        Long.class,
        runId);
  }

  private List<String> visited(String instance) {
    return historyService
        .createHistoricActivityInstanceQuery()
        .processInstanceId(instance)
        .list()
        .stream()
        .map(HistoricActivityInstance::getActivityId)
        .toList();
  }

  private String processDefinition(String instance) {
    return historyService
        .createHistoricProcessInstanceQuery()
        .processInstanceId(instance)
        .singleResult()
        .getProcessDefinitionId();
  }
}
