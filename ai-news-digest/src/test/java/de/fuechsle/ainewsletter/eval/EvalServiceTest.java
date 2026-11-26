package de.fuechsle.ainewsletter.eval;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.eval.EvalService.EvalResult;
import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Evaluation through the normal process with the fake provider (never real APIs in tests). */
class EvalServiceTest extends ProcessTestSupport {

  @Autowired private EvalService evalService;

  @TempDir Path output;

  @Test
  void runsTheTopicAndExportsEverythingNeededToJudgeThePrompts() throws IOException {
    String suffix = unique();
    long researcher = model("ok-research-" + suffix);
    long aggregator = model("ok-aggregator-" + suffix);
    String topicName = "Eval Ä " + suffix;
    topic(topicName, aggregator, researcher);

    List<EvalResult> results = evalService.run(List.of(topicName), output, Duration.ofSeconds(60));

    EvalResult result = results.getFirst();
    assertThat(result.status()).isEqualTo("COMPLETED");
    Path directory = result.directory();
    assertThat(directory.getParent().getFileName().toString()).isEqualTo("v7");
    assertThat(directory.getFileName().toString()).endsWith("-eval-ae-" + suffix);
    assertThat(directory.resolve("research-ok-research-" + suffix + ".txt")).exists();
    // Searches are stored with the research result and exported with the used hit marked
    assertThat(directory.resolve("searches-ok-research-" + suffix + ".md"))
        .content()
        .contains("`fake news November 2026`")
        .contains("✅ Spring Boot veröffentlicht – https://spring.example.test/blog/release")
        .contains("- Übersicht – https://digest.example.test/week (1 day ago)");
    assertThat(directory.resolve("summary.md")).content().contains("1 Suchbegriffe, 2 Treffer");
    assertThat(directory.resolve("aggregate.json")).content().contains("Fake-Digest");
    assertThat(directory.resolve("mail.html")).content().contains("Fake-Digest");
    assertThat(Files.size(directory.resolve("digest.pdf"))).isPositive();

    String runJson = Files.readString(directory.resolve("run.json"), StandardCharsets.UTF_8);
    JsonNode run = new JsonMapper().readTree(runJson);
    assertThat(run.get("status").asString()).isEqualTo("COMPLETED");
    assertThat(run.get("promptVersion").asString()).isEqualTo("v7");
    // research 0.0205 + aggregation 0.0105
    assertThat(run.get("totalCostUsd").decimalValue()).isEqualByComparingTo("0.031");
    JsonNode research = run.get("research").get(0);
    assertThat(research.get("status").asString()).isEqualTo("SUCCEEDED");
    assertThat(research.get("items")).hasSize(2);
    assertThat(research.has("rawResponse")).isFalse();
    assertThat(run.get("aggregation").get("model").asString()).isEqualTo("ok-aggregator-" + suffix);
    assertThat(run.get("mailDeliveries").get(0).asString()).isEqualTo("SENT");
    // Mail addresses are never exported
    assertThat(runJson).doesNotContain("@example.test");

    assertThat(directory.resolve("summary.md"))
        .content()
        .contains("Status: COMPLETED", "Kosten gesamt: 0.031", "2 (VALID 2)", "Aggregator");
  }

  @Test
  void unknownOrRunningTopicsAreSkippedAndAFailedRunIsExportedToo() {
    String suffix = unique();
    long failing = model("fail-" + suffix);
    String failedTopic = "Eval Fehler " + suffix;
    topic(failedTopic, failing, failing);

    List<EvalResult> results =
        evalService.run(
            List.of("Gibt es nicht " + suffix, failedTopic), output, Duration.ofSeconds(60));

    assertThat(results.get(0).skipReason()).isEqualTo("Topic existiert nicht");
    assertThat(results.get(0).runId()).isNull();
    assertThat(results.get(1).status()).isEqualTo("FAILED");
    assertThat(results.get(1).directory().resolve("run.json")).content().contains("FAILED");
    assertThat(results.get(1).directory().resolve("aggregate.json")).doesNotExist();
    assertThat(results.get(1).directory().resolve("summary.md")).content().contains("| 0 |");
  }

  @Test
  void runThatDoesNotEndInTimeIsExportedAsRunning() {
    String suffix = unique();
    long slow = model("slow-" + suffix);
    String topicName = "Eval langsam " + suffix;
    topic(topicName, slow, slow);

    try {
      List<EvalResult> results =
          evalService.run(List.of(topicName), output, Duration.ofMillis(500));

      assertThat(results.getFirst().status()).isEqualTo("RUNNING");
      assertThat(results.getFirst().directory().resolve("run.json")).content().contains("RUNNING");

      // A second eval of the same topic cannot start while the run is active
      assertThat(
              evalService
                  .run(List.of(topicName), output, Duration.ofMillis(1))
                  .getFirst()
                  .skipReason())
          .contains("läuft bereits");
    } finally {
      fakeProvider.release();
    }
    awaitEnd(
        jdbc.queryForObject(
            "SELECT r.process_instance_id FROM digest_run r JOIN topic t ON t.id = r.topic_id"
                + " WHERE t.name = ?",
            String.class,
            topicName));
  }

  @Test
  void slugKeepsOnlySafeCharacters() {
    assertThat(EvalExporter.slug("Java & KI: Überblick!")).isEqualTo("java-ki-ueberblick");
    assertThat(EvalExporter.slug("***")).isEqualTo("x");
  }
}
