package de.fuechsle.ainewsletter.research;

import java.util.List;

/**
 * Parsed research answer (JSON contract in docs/02-architektur.md).
 *
 * @param items valid items in the order of the model (most important first), at most the configured
 *     maximum
 * @param skippedItems items that did not satisfy the contract and were dropped
 */
public record ResearchResponse(List<NewsItemCandidate> items, int skippedItems) {

  public ResearchResponse {
    items = List.copyOf(items);
  }
}
