package de.fuechsle.ainewsletter.research;

import java.time.LocalDate;

/**
 * A news item as reported by a research model, before any check (untrusted content).
 *
 * @param publishedAt publication date or {@code null} if the model did not know it
 */
public record NewsItemCandidate(
    String headline, String summary, String url, LocalDate publishedAt) {}
