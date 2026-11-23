package de.fuechsle.ainewsletter.prompt;

/** A prompt template is missing or cannot be rendered with the given values. */
public class PromptException extends RuntimeException {

  public PromptException(String message) {
    super(message);
  }

  public PromptException(String message, Throwable cause) {
    super(message, cause);
  }
}
