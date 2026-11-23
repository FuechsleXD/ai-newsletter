package de.fuechsle.ainewsletter.service;

/** The topic does not exist. */
public class TopicNotFoundException extends RuntimeException {

  public TopicNotFoundException(long topicId) {
    super("Topic " + topicId + " does not exist");
  }
}
