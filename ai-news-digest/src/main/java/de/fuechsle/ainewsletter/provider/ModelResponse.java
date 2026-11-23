package de.fuechsle.ainewsletter.provider;

import java.time.Duration;

/**
 * Answer of a model call. The text is untrusted (golden rule 3).
 *
 * @param text answer text (all text parts joined)
 * @param inputTokens billed input tokens
 * @param outputTokens billed output tokens (including thinking)
 * @param webSearches number of billed web searches
 * @param stopReason why the model stopped, as reported by the provider (e.g. {@code END_TURN},
 *     {@code MAX_TOKENS}, {@code REFUSAL})
 * @param duration wall-clock duration of the call
 */
public record ModelResponse(
    String text,
    long inputTokens,
    long outputTokens,
    long webSearches,
    String stopReason,
    Duration duration) {

  /** Stop reason when the provider returned no content at all (reason unknown). */
  public static final String NO_CONTENT = "NO_CONTENT";

  /** The provider returned no content; usage may be missing as well. */
  public boolean noContent() {
    return NO_CONTENT.equals(stopReason);
  }

  /** The model declined the request (safety classifier). */
  public boolean refused() {
    return "REFUSAL".equalsIgnoreCase(stopReason);
  }

  /** The answer was cut off at the output limit. */
  public boolean truncated() {
    return "MAX_TOKENS".equalsIgnoreCase(stopReason);
  }
}
