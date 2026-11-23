package de.fuechsle.ainewsletter.provider;

/**
 * The answer of a model breaks its JSON contract. Leads to a repair attempt with the same model
 * (see {@link JsonModelCall}).
 */
public class AnswerParseException extends RuntimeException {

  public AnswerParseException(String message) {
    super(message);
  }

  public AnswerParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
