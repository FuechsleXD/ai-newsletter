package de.fuechsle.ainewsletter.secret;

/** A secret is missing or empty. The message names the secret, never its value. */
public class SecretNotFoundException extends RuntimeException {

  public SecretNotFoundException(String message) {
    super(message);
  }
}
