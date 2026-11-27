package de.fuechsle.ainewsletter.provider;

import de.fuechsle.ainewsletter.cost.CostCalculator;
import de.fuechsle.ainewsletter.secret.SecretNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The calls of one model for one task (the first call plus repair attempts). Collects usage,
 * duration and cost over all calls, because every call is billed, also when the task fails in the
 * end (golden rule 5). Not thread-safe; one instance per task.
 */
public final class ModelCalls {

  private static final Logger log = LoggerFactory.getLogger(ModelCalls.class);

  private final NewsProviderRegistry providers;
  private final CostCalculator costs;
  private final ModelSnapshot model;
  private final List<ModelResponse> responses = new ArrayList<>();
  private String lastText;
  private String lastError;

  public ModelCalls(NewsProviderRegistry providers, CostCalculator costs, ModelSnapshot model) {
    this.providers = providers;
    this.costs = costs;
    this.model = model;
  }

  /**
   * Calls the model. Errors of the provider or a missing secret do not throw.
   *
   * <p>If no call slot of the provider becomes free in time, the first call of the task throws
   * {@link CallSlotUnavailableException}, so the engine retries the job; nothing was billed yet. A
   * later call (repair attempt) fails like a provider error instead, so the cost of the calls
   * before is still recorded (golden rule 5).
   *
   * @return the response, or {@code null} if the call failed ({@link #lastError()} is set)
   * @throws CallSlotUnavailableException if no slot became free for the first call
   */
  public ModelResponse call(ModelInvocation invocation) {
    try {
      ModelResponse response = providers.get(model.providerCode()).call(invocation);
      responses.add(response);
      lastText = response.text();
      return response;
    } catch (CallSlotUnavailableException e) {
      if (responses.isEmpty()) {
        throw e;
      }
      return failed(e);
    } catch (ProviderException | SecretNotFoundException e) {
      return failed(e);
    }
  }

  private ModelResponse failed(RuntimeException e) {
    log.warn("Call of model {} failed: {}", model.modelName(), e.getMessage());
    lastError = e.getMessage();
    return null;
  }

  public ModelSnapshot model() {
    return model;
  }

  /** Text of the last successful call, {@code null} if there was none. */
  public String lastText() {
    return lastText;
  }

  /** Error of the last failed call, {@code null} if there was none. */
  public String lastError() {
    return lastError;
  }

  public long inputTokens() {
    return responses.stream().mapToLong(ModelResponse::inputTokens).sum();
  }

  public long outputTokens() {
    return responses.stream().mapToLong(ModelResponse::outputTokens).sum();
  }

  public long webSearches() {
    return responses.stream().mapToLong(ModelResponse::webSearches).sum();
  }

  /** Searches of all calls, in call order (repair calls do not search). */
  public SearchTrace searchTrace() {
    return responses.stream()
        .map(ModelResponse::searchTrace)
        .reduce(SearchTrace.EMPTY, SearchTrace::plus);
  }

  public Duration duration() {
    return responses.stream().map(ModelResponse::duration).reduce(Duration.ZERO, Duration::plus);
  }

  /** Cost of all calls with the price snapshot of the model. */
  public BigDecimal cost() {
    return costs.cost(model.prices(), inputTokens(), outputTokens(), webSearches());
  }
}
