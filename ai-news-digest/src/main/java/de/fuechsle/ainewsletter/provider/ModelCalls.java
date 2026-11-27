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

  /** Appended to the error of a call that broke off and was costed with an estimate. */
  static final String ABORTED_ESTIMATE =
      "; Kosten geschätzt (Abbruch): Prompt und höchstmögliche Websuchen, Suchinhalte unbekannt";

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
    } catch (ProviderException e) {
      if (e.estimatedUsage() == null) {
        return failed(e);
      }
      // The provider probably billed the call although it broke off: book the estimate
      responses.add(e.estimatedUsage());
      log.warn("Call of model {} broke off: {}", model.modelName(), e.getMessage());
      lastError = e.getMessage() + ABORTED_ESTIMATE;
      return null;
    } catch (SecretNotFoundException e) {
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

  /**
   * Cost of all calls with the price snapshot of the model. Each call is priced on its own, because
   * a price tier depends on the prompt length of the single call.
   */
  public BigDecimal cost() {
    return responses.stream()
        .map(r -> costs.cost(model.prices(), r.inputTokens(), r.outputTokens(), r.webSearches()))
        .reduce(BigDecimal.ZERO.setScale(CostCalculator.SCALE), BigDecimal::add);
  }

  /** Whether any call was costed with an estimate instead of reported usage. */
  public boolean usageEstimated() {
    return responses.stream().anyMatch(ModelResponse::usageEstimated);
  }
}
