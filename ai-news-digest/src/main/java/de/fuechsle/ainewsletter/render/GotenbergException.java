package de.fuechsle.ainewsletter.render;

/** Gotenberg could not render the PDF (not reachable, timeout, error answer); the job retries. */
public class GotenbergException extends RuntimeException {

  public GotenbergException(String message, Throwable cause) {
    super(message, cause);
  }
}
