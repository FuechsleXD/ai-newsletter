package de.fuechsle.ainewsletter.research;

import de.fuechsle.ainewsletter.domain.ProviderResultStatus;
import de.fuechsle.ainewsletter.provider.SearchTrace;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Outcome of one research model, including a repair attempt. Maps 1:1 to a {@code provider_result}
 * row with its {@code news_item} rows. A failure is a result, not an exception (a failed model
 * never aborts the run).
 *
 * @param rawResponse last answer text of the model (untrusted), {@code null} if no answer arrived
 * @param inputTokens input tokens of all calls (research and repair)
 * @param outputTokens output tokens of all calls
 * @param webSearches web searches of all calls
 * @param costUsd cost of all calls with the prices at run time; also set for failed results
 * @param duration total duration of all calls
 * @param errorMessage reason for {@code FAILED}, {@code null} otherwise
 */
public record ResearchResult(
    ProviderResultStatus status,
    List<NewsItemCandidate> items,
    String rawResponse,
    long inputTokens,
    long outputTokens,
    long webSearches,
    BigDecimal costUsd,
    Duration duration,
    String errorMessage,
    SearchTrace searchTrace) {

  public ResearchResult {
    items = List.copyOf(items);
    searchTrace = searchTrace == null ? SearchTrace.EMPTY : searchTrace;
  }
}
