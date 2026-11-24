package de.fuechsle.ainewsletter.service;

/**
 * The request conflicts with the current state; answered with 409 (docs/05-api.md). The message is
 * shown to the user and therefore German.
 */
public abstract class ConflictException extends RuntimeException {

  protected ConflictException(String message) {
    super(message);
  }
}
