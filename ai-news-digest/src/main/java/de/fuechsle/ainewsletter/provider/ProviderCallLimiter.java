package de.fuechsle.ainewsletter.provider;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.domain.AiProvider;
import de.fuechsle.ainewsletter.repository.AiProviderRepository;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limits the concurrent calls per provider, so that parallel research instances do not run into the
 * rate limits of a provider (docs/04-prozess-bpmn.md, "Parallele Recherche"). One fair semaphore
 * per provider code; every call (research, repair attempt, aggregation) takes one slot. The number
 * of slots is {@code ai_provider.max_concurrent_calls}, or {@code
 * newsdigest.ai.max-concurrent-calls-per-provider} if it is NULL. It is read with the first call of
 * a provider; a change takes effect after a restart (reload of the configuration: roadmap step 16).
 *
 * <p>Waiting for a slot is limited to {@code newsdigest.ai.max-wait-for-call-slot}. The start
 * checks that waiting plus the longest possible calls of one job stay below the job lock time;
 * otherwise the engine could hand a job that is still waiting or calling to a second thread, and
 * the model would be called (and billed) twice.
 */
@Component
public class ProviderCallLimiter {

  private final ToIntFunction<String> slotsOfProvider;
  private final Duration maxWait;
  private final ConcurrentMap<String, Semaphore> slots = new ConcurrentHashMap<>();

  @Autowired
  public ProviderCallLimiter(NewsDigestProperties properties, AiProviderRepository providers) {
    this(
        code ->
            providers
                .findByCode(code)
                .map(AiProvider::getMaxConcurrentCalls)
                .orElse(properties.ai().maxConcurrentCallsPerProvider()),
        properties.ai().maxWaitForCallSlot());
    check(properties.ai(), properties.process());
  }

  ProviderCallLimiter(int slotsPerProvider, Duration maxWait) {
    this(code -> slotsPerProvider, maxWait);
  }

  private ProviderCallLimiter(ToIntFunction<String> slotsOfProvider, Duration maxWait) {
    this.slotsOfProvider = slotsOfProvider;
    this.maxWait = maxWait;
  }

  /** Without a practical limit, for tests that build services by hand. */
  public static ProviderCallLimiter unlimited() {
    return new ProviderCallLimiter(Integer.MAX_VALUE, Duration.ZERO);
  }

  /**
   * Throws if a job could still be waiting or calling when its lock expires: maximum wait plus the
   * call timeout for the first call and every repair attempt must be shorter than the lock.
   */
  static void check(NewsDigestProperties.Ai ai, NewsDigestProperties.Process process) {
    Duration longestJob =
        ai.maxWaitForCallSlot().plus(ai.callTimeout().multipliedBy(1L + ai.jsonRepairAttempts()));
    if (longestJob.compareTo(process.jobLockTime()) >= 0) {
      throw new IllegalStateException(
          "newsdigest.ai.max-wait-for-call-slot ("
              + ai.maxWaitForCallSlot()
              + ") plus call-timeout ("
              + ai.callTimeout()
              + ") times "
              + (1 + ai.jsonRepairAttempts())
              + " calls must be shorter than newsdigest.process.job-lock-time ("
              + process.jobLockTime()
              + "), otherwise a running job can be executed twice");
    }
  }

  /**
   * Runs the call in a slot of the provider.
   *
   * @throws CallSlotUnavailableException if no slot became free in time
   */
  public <T> T call(String providerCode, Supplier<T> call) {
    Semaphore semaphore =
        slots.computeIfAbsent(
            providerCode, code -> new Semaphore(slotsOfProvider.applyAsInt(code), true));
    acquire(providerCode, semaphore);
    try {
      return call.get();
    } finally {
      semaphore.release();
    }
  }

  private void acquire(String providerCode, Semaphore semaphore) {
    try {
      if (!semaphore.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new CallSlotUnavailableException(providerCode, maxWait);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CallSlotUnavailableException(providerCode, e);
    }
  }
}
