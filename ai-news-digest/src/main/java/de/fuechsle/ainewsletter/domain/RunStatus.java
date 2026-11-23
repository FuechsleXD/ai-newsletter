package de.fuechsle.ainewsletter.domain;

/** Status of a digest run (docs/04-prozess-bpmn.md). */
public enum RunStatus {
  RUNNING,
  COMPLETED,
  NO_NEWS,
  FAILED,
  SKIPPED_BUDGET,
  CANCELLED
}
