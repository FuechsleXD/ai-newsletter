package de.fuechsle.ainewsletter.provider;

import java.time.Duration;

/**
 * No call slot of a provider became free in time ({@link ProviderCallLimiter}). Unlike a {@link
 * ProviderException} the model did not fail, so this is not a {@code FAILED} result: the exception
 * leaves the delegate and the engine retries the job later (failed job retry cycle). Only thrown
 * before anything was billed.
 */
public class CallSlotUnavailableException extends RuntimeException {

  public CallSlotUnavailableException(String providerCode, Duration waited) {
    super("No call slot of provider " + providerCode + " became free within " + waited);
  }

  public CallSlotUnavailableException(String providerCode, Throwable cause) {
    super("Waiting for a call slot of provider " + providerCode + " was interrupted", cause);
  }
}
