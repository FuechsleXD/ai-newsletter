package de.fuechsle.ainewsletter.process;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Checks of step 14 through the whole process: unreachable sources and items of earlier runs never
 * reach the aggregator, which gets the headlines of the previous briefing instead. The URL check is
 * the fake of the test context (no HTTP requests).
 */
class NewsCheckProcessTest extends ProcessTestSupport {

  @Autowired private DigestProcessStarter starter;

  @Test
  void unreachableSourceAndEarlierItemsAreSortedOutAndPreviousHeadlinesArePassed() {
    String suffix = unique();
    long gone = model("gone-" + suffix);
    long topic = topic("Prüfung " + suffix, model("ok-aggregator-" + suffix), gone);

    // First run: the item with the unreachable source is sorted out before the aggregation
    String first = starter.start(topic);
    awaitEnd(first);
    assertThat(runStatus(first)).isEqualTo("COMPLETED");
    assertThat(statuses(runId(first)))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "https://unreachable.example.test/gone", "UNREACHABLE",
                "https://jdk.example.test/ea", "VALID"));
    String firstPrompt = fakeProvider.lastAggregationPrompt();
    assertThat(firstPrompt).doesNotContain("unreachable.example.test").contains("jdk.example.test");
    assertThat(firstPrompt).contains("PREVIOUS BRIEFING").containsPattern("\\):\\R+none\\R");

    // Second run with another model: the item of the first run is a duplicate of an earlier run,
    // the new one goes to the aggregator together with the headlines of the first briefing
    jdbc.update("DELETE FROM topic_model WHERE topic_id = ?", topic);
    jdbc.update(
        "INSERT INTO topic_model (topic_id, model_id) VALUES (?, ?)", topic, model("ok-" + suffix));
    String second = starter.start(topic);
    awaitEnd(second);

    assertThat(runStatus(second)).isEqualTo("COMPLETED");
    assertThat(statuses(runId(second)))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "https://spring.example.test/blog/release", "VALID",
                "https://jdk.example.test/ea", "DUPLICATE_PREVIOUS"));
    String secondPrompt = fakeProvider.lastAggregationPrompt();
    assertThat(secondPrompt).contains("spring.example.test").doesNotContain("jdk.example.test");
    assertThat(secondPrompt).containsPattern("PREVIOUS BRIEFING[^\\n]*\\R+- Thema \\d+");
  }

  @Test
  void runWithOnlyItemsOfEarlierRunsEndsWithoutNews() {
    String suffix = unique();
    long topic =
        topic("Wiederholung " + suffix, model("ok-aggregator-" + suffix), model("ok-" + suffix));

    String first = starter.start(topic);
    awaitEnd(first);
    String second = starter.start(topic);
    awaitEnd(second);

    assertThat(runStatus(second)).isEqualTo("NO_NEWS");
    assertThat(statuses(runId(second)).values()).containsOnly("DUPLICATE_PREVIOUS");
  }

  private Map<String, String> statuses(long runId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT n.normalized_url, n.status FROM news_item n"
                + " JOIN provider_result p ON p.id = n.provider_result_id"
                + " WHERE p.digest_run_id = ?",
            runId);
    return rows.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                row -> (String) row.get("normalized_url"), row -> (String) row.get("status")));
  }
}
