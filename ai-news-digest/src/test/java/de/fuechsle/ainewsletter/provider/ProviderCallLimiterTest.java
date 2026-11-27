package de.fuechsle.ainewsletter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.domain.AiProvider;
import de.fuechsle.ainewsletter.repository.AiProviderRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ProviderCallLimiterTest {

  @Test
  void atMostTheConfiguredNumberOfCallsRunAtTheSameTime() throws Exception {
    ProviderCallLimiter limiter = new ProviderCallLimiter(2, Duration.ofSeconds(10));
    AtomicInteger active = new AtomicInteger();
    AtomicInteger max = new AtomicInteger();
    CountDownLatch gate = new CountDownLatch(1);

    ExecutorService executor = Executors.newFixedThreadPool(5);
    try {
      List<Future<Integer>> calls = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        calls.add(
            executor.submit(() ->
                limiter.call(
                    "ANTHROPIC",
                    () -> {
                      max.accumulateAndGet(active.incrementAndGet(), Math::max);
                      awaitQuietly(gate);
                      active.decrementAndGet();
                      return 1;
                    })));
      }
      waitUntil(() -> active.get() == 2);
      Thread.sleep(200);
      assertThat(active.get()).isEqualTo(2);

      gate.countDown();
      for (Future<Integer> call : calls) {
        assertThat(call.get(10, TimeUnit.SECONDS)).isEqualTo(1);
      }
      assertThat(max.get()).isEqualTo(2);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void callWithoutFreeSlotInTimeIsRetriedLaterNotFailed() throws Exception {
    ProviderCallLimiter limiter = new ProviderCallLimiter(1, Duration.ofMillis(100));
    CountDownLatch inside = new CountDownLatch(1);
    CountDownLatch gate = new CountDownLatch(1);
    Thread holder =
        Thread.ofPlatform()
            .start(() ->
                limiter.call(
                    "ANTHROPIC",
                    () -> {
                      inside.countDown();
                      awaitQuietly(gate);
                      return null;
                    }));
    try {
      assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> limiter.call("ANTHROPIC", () -> "never"))
          .isInstanceOf(CallSlotUnavailableException.class)
          .isNotInstanceOf(ProviderException.class)
          .hasMessageContaining("ANTHROPIC");
      // Another provider has its own slots
      assertThat(limiter.call("OTHER", () -> "ok")).isEqualTo("ok");
    } finally {
      gate.countDown();
      holder.join(5_000);
    }
    // The slot is free again after the call, also after an exception
    assertThatThrownBy(() ->
            limiter.call(
                "ANTHROPIC",
                () -> {
                  throw new ProviderException("boom");
                }))
        .isInstanceOf(ProviderException.class);
    assertThat(limiter.call("ANTHROPIC", () -> "again")).isEqualTo("again");
  }

  @Test
  void interruptedWaitKeepsTheInterruptFlag() {
    ProviderCallLimiter limiter = new ProviderCallLimiter(1, Duration.ofSeconds(10));

    // An interrupted thread does not wait for a slot (the job executor shuts down)
    Thread.currentThread().interrupt();
    assertThatThrownBy(() -> limiter.call("ANTHROPIC", () -> "never"))
        .isInstanceOf(CallSlotUnavailableException.class)
        .hasMessageContaining("interrupted");
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  void registryRunsEveryCallThroughTheLimiter() {
    ProviderCallLimiter limiter = new ProviderCallLimiter(1, Duration.ofMillis(50));
    NewsProvider provider =
        new NewsProvider() {
          @Override
          public String code() {
            return "ANTHROPIC";
          }

          @Override
          public ModelResponse call(ModelInvocation invocation) {
            // A call inside a call needs a second slot, which does not exist
            return limiter.call("ANTHROPIC", () -> null);
          }
        };
    NewsProviderRegistry registry = new NewsProviderRegistry(List.of(provider), limiter);

    assertThat(registry.get("ANTHROPIC").code()).isEqualTo("ANTHROPIC");
    assertThatThrownBy(() -> registry.get("ANTHROPIC").call(null))
        .isInstanceOf(CallSlotUnavailableException.class);
  }

  @Test
  void waitAndCallsOfOneJobMustStayBelowTheJobLock() {
    // Defaults: 5 min wait + 2 x 300 s calls = 15 min < 20 min lock
    assertThatCode(() -> ProviderCallLimiter.check(properties().ai(), properties().process()))
        .doesNotThrowAnyException();

    NewsDigestProperties tooLong = properties("newsdigest.ai.max-wait-for-call-slot", "10m");
    assertThatThrownBy(() -> ProviderCallLimiter.check(tooLong.ai(), tooLong.process()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("max-wait-for-call-slot")
        .hasMessageContaining("job-lock-time");
    assertThatThrownBy(() -> new ProviderCallLimiter(tooLong, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void slotsComeFromTheProviderRowOrTheDefault() {
    AiProvider limited = mock(AiProvider.class);
    when(limited.getMaxConcurrentCalls()).thenReturn(1);
    AiProviderRepository providers = mock(AiProviderRepository.class);
    when(providers.findByCode("LIMITED")).thenReturn(Optional.of(limited));
    AiProvider withoutLimit = mock(AiProvider.class);
    when(withoutLimit.getMaxConcurrentCalls()).thenReturn(null);
    when(providers.findByCode("DEFAULT")).thenReturn(Optional.of(withoutLimit));
    when(providers.findByCode("UNKNOWN")).thenReturn(Optional.empty());
    ProviderCallLimiter limiter =
        new ProviderCallLimiter(
            properties("newsdigest.ai.max-wait-for-call-slot", "50ms"), providers);

    // One slot: a call inside a call of the same provider finds none
    assertThatThrownBy(() -> limiter.call("LIMITED", () -> limiter.call("LIMITED", () -> "x")))
        .isInstanceOf(CallSlotUnavailableException.class);
    // NULL and unknown providers get the default of 2 slots
    assertThat(limiter.call("DEFAULT", () -> limiter.call("DEFAULT", () -> "x"))).isEqualTo("x");
    assertThat(limiter.call("UNKNOWN", () -> limiter.call("UNKNOWN", () -> "x"))).isEqualTo("x");
  }

  private static NewsDigestProperties properties(String... keyValues) {
    java.util.Map<String, String> values = new java.util.HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      values.put(keyValues[i], keyValues[i + 1]);
    }
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate("newsdigest", Bindable.of(NewsDigestProperties.class));
  }

  private static String awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return null;
  }

  private static void waitUntil(java.util.function.BooleanSupplier condition)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Condition not reached in time");
      }
      Thread.sleep(10);
    }
  }
}
