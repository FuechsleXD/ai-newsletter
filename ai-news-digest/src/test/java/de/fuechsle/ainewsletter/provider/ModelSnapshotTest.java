package de.fuechsle.ainewsletter.provider;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.domain.AiModel;
import de.fuechsle.ainewsletter.domain.AiProvider;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ModelSnapshotTest {

  @Test
  void copiesModelAndProviderValuesFromTheDatabaseRows() {
    AiProvider provider = new AiProvider("ANTHROPIC", "Anthropic", "newsdigest/test/ai/anthropic");
    // Test values only, not real model IDs or prices
    AiModel model =
        new AiModel(provider, "test-model-1", "Test", true, true, BigDecimal.ONE, BigDecimal.TEN);
    model.setMaxOutputTokens(8_000);
    model.setSupportsTemperature(true);
    model.setTemperature(new BigDecimal("0.50"));
    model.setPricePerSearchCall(new BigDecimal("0.02"));

    ModelSnapshot snapshot = ModelSnapshot.of(model);

    assertThat(snapshot.modelId()).isNull();
    assertThat(snapshot.providerCode()).isEqualTo("ANTHROPIC");
    assertThat(snapshot.secretName()).isEqualTo("newsdigest/test/ai/anthropic");
    assertThat(snapshot.modelName()).isEqualTo("test-model-1");
    assertThat(snapshot.maxOutputTokens()).isEqualTo(8_000);
    assertThat(snapshot.temperature()).isEqualByComparingTo("0.5");
    assertThat(snapshot.supportsWebSearch()).isTrue();
    assertThat(snapshot.prices().inputPerMtok()).isEqualByComparingTo("1");
    assertThat(snapshot.prices().outputPerMtok()).isEqualByComparingTo("10");
    assertThat(snapshot.prices().perSearchCall()).isEqualByComparingTo("0.02");
  }

  @Test
  void stopReasonsAreInterpreted() {
    assertThat(response("MAX_TOKENS").truncated()).isTrue();
    assertThat(response("END_TURN").truncated()).isFalse();
    assertThat(response("refusal").refused()).isTrue();
    assertThat(response(ModelResponse.NO_CONTENT).noContent()).isTrue();
  }

  private static ModelResponse response(String stopReason) {
    return new ModelResponse("", 0, 0, 0, stopReason, Duration.ZERO);
  }
}
