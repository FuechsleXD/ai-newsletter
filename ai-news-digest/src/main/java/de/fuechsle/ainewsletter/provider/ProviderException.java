package de.fuechsle.ainewsletter.provider;

/**
 * A model call failed (network, timeout, HTTP error, missing key). The message must not contain
 * secrets; callers turn it into a {@code FAILED} result, not into an aborted run.
 *
 * <p>If the request may have reached the provider before it broke off (timeout, connection lost),
 * {@link #estimatedUsage()} carries an estimate of what the provider bills, so the call is still
 * costed (golden rule 5, ADR-014). An answered HTTP error is not billed and has none.
 */
public class ProviderException extends RuntimeException {

  private final transient ModelResponse estimatedUsage;

  public ProviderException(String message) {
    this(message, null, null);
  }

  public ProviderException(String message, Throwable cause) {
    this(message, cause, null);
  }

  public ProviderException(String message, Throwable cause, ModelResponse estimatedUsage) {
    super(message, cause);
    this.estimatedUsage = estimatedUsage;
  }

  /** Estimated usage of a call that broke off, {@code null} if nothing was billed. */
  public ModelResponse estimatedUsage() {
    return estimatedUsage;
  }
}
