package de.fuechsle.ainewsletter.service;

/** A run of the topic is still RUNNING; at most one active run per topic is allowed. */
public class RunAlreadyActiveException extends RuntimeException {

  public RunAlreadyActiveException(long topicId) {
    super("Topic " + topicId + " already has a running digest run");
  }
}
