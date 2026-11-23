package de.fuechsle.ainewsletter.service;

/** Result of the research phase; decides the path after the join (process variable). */
public enum ResearchOutcome {
  /** No research model succeeded. */
  NONE_SUCCEEDED,
  /** At least one model succeeded, but there is no valid news item. */
  NO_VALID_NEWS,
  /** There are valid news items to aggregate. */
  VALID_NEWS
}
