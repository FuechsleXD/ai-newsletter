package de.fuechsle.ainewsletter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.cost.CostCalculator;
import de.fuechsle.ainewsletter.cost.ModelPrices;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModelCallsTest {

  private static final ModelSnapshot MODEL =
      new ModelSnapshot(
          1L,
          "TEST",
          "newsdigest/test/ai/test",
          "test-model-1",
          null,
          null,
          true,
          new ModelPrices(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

  /** Answers the first call, then finds no free slot. */
  private final NewsProvider provider =
      new NewsProvider() {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String code() {
          return "TEST";
        }

        @Override
        public ModelResponse call(ModelInvocation invocation) {
          if (calls.incrementAndGet() > 1) {
            throw new CallSlotUnavailableException("TEST", Duration.ofMinutes(5));
          }
          return new ModelResponse("{}", 1_000_000, 0, 0, "END_TURN", Duration.ZERO);
        }
      };

  private final ModelCalls calls =
      new ModelCalls(new NewsProviderRegistry(List.of(provider)), new CostCalculator(), MODEL);

  @Test
  void noSlotForTheFirstCallLeavesTheTaskSoTheJobIsRetried() {
    NewsProvider busy =
        new NewsProvider() {
          @Override
          public String code() {
            return "TEST";
          }

          @Override
          public ModelResponse call(ModelInvocation invocation) {
            throw new CallSlotUnavailableException("TEST", Duration.ofMinutes(5));
          }
        };
    ModelCalls first =
        new ModelCalls(new NewsProviderRegistry(List.of(busy)), new CostCalculator(), MODEL);

    assertThatThrownBy(() -> first.call(null)).isInstanceOf(CallSlotUnavailableException.class);
  }

  @Test
  void callThatBrokeOffIsCostedWithItsEstimate() {
    ModelResponse estimate =
        new ModelResponse("", 500_000, 0, 3, "ABORTED", Duration.ofMinutes(5), true);
    NewsProvider aborting =
        new NewsProvider() {
          @Override
          public String code() {
            return "TEST";
          }

          @Override
          public ModelResponse call(ModelInvocation invocation) {
            throw new ProviderException("Request failed", null, estimate);
          }
        };
    ModelCalls aborted =
        new ModelCalls(new NewsProviderRegistry(List.of(aborting)), new CostCalculator(), MODEL);

    assertThat(aborted.call(null)).isNull();

    assertThat(aborted.lastError()).startsWith("Request failed").contains("geschätzt (Abbruch)");
    assertThat(aborted.lastText()).isNull();
    assertThat(aborted.usageEstimated()).isTrue();
    // 0.5 MTok at 1 USD plus 3 searches at 1 USD
    assertThat(aborted.cost()).isEqualByComparingTo("3.5");
  }

  @Test
  void noSlotForARepairAttemptFailsLikeAProviderErrorAndKeepsTheBilledCall() {
    assertThat(calls.call(null)).isNotNull();

    assertThat(calls.call(null)).isNull();

    assertThat(calls.lastError()).contains("No call slot");
    // The first call stays billed (1 MTok input at 1 USD)
    assertThat(calls.inputTokens()).isEqualTo(1_000_000);
  }
}
