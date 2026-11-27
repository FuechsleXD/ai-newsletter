package de.fuechsle.ainewsletter.service;

import de.fuechsle.ainewsletter.domain.RunStatus;

/** Only a run with status RUNNING can be cancelled. */
public class RunNotRunningException extends ConflictException {

  public RunNotRunningException(long runId, RunStatus status) {
    super("Lauf " + runId + " läuft nicht mehr (Status " + status + ")");
  }
}
