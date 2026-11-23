package de.fuechsle.ainewsletter.storage;

/** A PDF could not be stored or loaded. */
public class StorageException extends RuntimeException {

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
