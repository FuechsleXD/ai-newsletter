package de.fuechsle.ainewsletter.provider;

/**
 * A model call failed (network, timeout, HTTP error, missing key). The message must not contain
 * secrets; callers turn it into a {@code FAILED} result, not into an aborted run.
 */
public class ProviderException extends RuntimeException {

  public ProviderException(String message) {
    super(message);
  }

  public ProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
