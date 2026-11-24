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
 * @param usageEstimated the provider reported no usage, the token counts are an estimate (see
 *     ADR-014)
 */
public record ModelResponse(
    String text,
    long inputTokens,
    long outputTokens,
    long webSearches,
    String stopReason,
    Duration duration,
    boolean usageEstimated) {

  /** Stop reason when the provider returned no content at all (reason unknown). */
  public static final String NO_CONTENT = "NO_CONTENT";

  /** Answer with usage reported by the provider. */
  public ModelResponse(
      String text,
      long inputTokens,
      long outputTokens,
      long webSearches,
      String stopReason,
      Duration duration) {
    this(text, inputTokens, outputTokens, webSearches, stopReason, duration, false);
  }

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

  /** The provider paused a long server-side tool turn (e.g. many web searches) unfinished. */
  public boolean paused() {
    return "PAUSE_TURN".equalsIgnoreCase(stopReason);
  }
}
