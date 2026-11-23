package de.fuechsle.ainewsletter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DigestRunTest {

  private static final Instant START = Instant.parse("2026-11-23T06:00:00Z");

  @Test
  void finishSetsStatusAndEndTime() {
    DigestRun run = run();

    run.finish(RunStatus.NO_NEWS, START.plusSeconds(60));

    assertThat(run.getStatus()).isEqualTo(RunStatus.NO_NEWS);
    assertThat(run.getFinishedAt()).isEqualTo(START.plusSeconds(60));
  }

  @Test
  void runCannotFinishAsRunning() {
    assertThatThrownBy(() -> run().finish(RunStatus.RUNNING, START))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static DigestRun run() {
    AiProvider provider = new AiProvider("TEST", "Test", "newsdigest/test/ai/test");
    AiModel model =
        new AiModel(provider, "test-model-1", "Test", true, true, BigDecimal.ONE, BigDecimal.ONE);
    Topic topic = new Topic("Test", new Schedule("s", "0 0 7 * * MON", "UTC"), model);
    return new DigestRun(
        topic, model, "v1", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23), START);
  }
}
