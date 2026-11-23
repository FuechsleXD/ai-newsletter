package de.fuechsle.ainewsletter.support;

import de.fuechsle.ainewsletter.provider.ModelInvocation;
import de.fuechsle.ainewsletter.provider.ModelResponse;
import de.fuechsle.ainewsletter.provider.NewsProvider;
import de.fuechsle.ainewsletter.provider.ProviderException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provider {@code FAKE} for process tests, never calls a real API (golden rule 10). The behaviour
 * is chosen by the prefix of the model name:
 *
 * <ul>
 *   <li>{@code ok-}: two valid news items
 *   <li>{@code empty-}: an empty list
 *   <li>{@code fail-}: provider error
 *   <li>{@code slow-}: waits until {@link #release()} is called, then two valid items
 * </ul>
 */
public class FakeNewsProvider implements NewsProvider {

  public static final String CODE = "FAKE";

  private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
  private volatile CountDownLatch slowEntered = new CountDownLatch(1);
  private volatile CountDownLatch slowRelease = new CountDownLatch(1);

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public ModelResponse call(ModelInvocation invocation) {
    String model = invocation.modelName();
    calls.computeIfAbsent(model, key -> new AtomicInteger()).incrementAndGet();
    if (model.startsWith("fail-")) {
      throw new ProviderException("Fake provider error for " + model);
    }
    if (model.startsWith("empty-")) {
      return response("{\"items\": []}");
    }
    if (model.startsWith("slow-")) {
      slowEntered.countDown();
      await(slowRelease);
    }
    return response(Fixtures.read("research/valid.json"));
  }

  /** Number of calls of a model. */
  public int calls(String modelName) {
    AtomicInteger count = calls.get(modelName);
    return count == null ? 0 : count.get();
  }

  /** Waits until a {@code slow-} model has been called. */
  public void awaitSlowCall() {
    await(slowEntered);
  }

  /** Lets waiting {@code slow-} models continue and arms the latches again. */
  public void release() {
    slowRelease.countDown();
    slowEntered = new CountDownLatch(1);
    slowRelease = new CountDownLatch(1);
  }

  private static ModelResponse response(String text) {
    return new ModelResponse(text, 1_000, 500, 1, "END_TURN", Duration.ofMillis(10));
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Fake provider waited too long");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
