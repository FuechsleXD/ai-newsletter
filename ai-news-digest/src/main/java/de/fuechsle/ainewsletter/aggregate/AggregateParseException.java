package de.fuechsle.ainewsletter.aggregate;

import de.fuechsle.ainewsletter.provider.AnswerParseException;

/** The aggregator answer breaks the JSON contract; triggers the repair attempt. */
public class AggregateParseException extends AnswerParseException {

  public AggregateParseException(String message) {
    super(message);
  }

  public AggregateParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
