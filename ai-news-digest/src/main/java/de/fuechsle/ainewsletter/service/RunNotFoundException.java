package de.fuechsle.ainewsletter.service;

/** The digest run does not exist. */
public class RunNotFoundException extends NotFoundException {

  public RunNotFoundException(long runId) {
    super("Lauf " + runId + " existiert nicht");
  }
}
