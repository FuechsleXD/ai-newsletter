package de.fuechsle.ainewsletter.research;

/** The research answer is not valid JSON in the expected structure; triggers the repair attempt. */
public class ResearchParseException extends RuntimeException {

  public ResearchParseException(String message) {
    super(message);
  }

  public ResearchParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
