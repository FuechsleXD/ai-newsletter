package de.fuechsle.ainewsletter.service;

/** The topic does not exist. */
public class TopicNotFoundException extends NotFoundException {

  public TopicNotFoundException(long topicId) {
    super("Topic " + topicId + " existiert nicht");
  }
}
