package de.fuechsle.ainewsletter.service;

/**
 * A requested resource does not exist; answered with 404 (docs/05-api.md). The message is shown to
 * the user and therefore German.
 */
public abstract class NotFoundException extends RuntimeException {

  protected NotFoundException(String message) {
    super(message);
  }
}
