package de.fuechsle.ainewsletter.research;

import de.fuechsle.ainewsletter.provider.AnswerParseException;

/** The research answer is not valid JSON in the expected structure; triggers the repair attempt. */
public class ResearchParseException extends AnswerParseException {

  public ResearchParseException(String message) {
    super(message);
  }

  public ResearchParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
