package de.fuechsle.ainewsletter.service;

/** A run of the topic is still RUNNING; at most one active run per topic is allowed. */
public class RunAlreadyActiveException extends ConflictException {

  public RunAlreadyActiveException(long topicId) {
    super("Für Topic " + topicId + " läuft bereits ein Lauf");
  }
}
