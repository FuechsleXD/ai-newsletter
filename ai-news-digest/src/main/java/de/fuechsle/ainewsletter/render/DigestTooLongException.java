package de.fuechsle.ainewsletter.render;

/**
 * The digest does not fit on one page even with shortened texts. The render job fails and the
 * engine retries it; after the last retry the run ends in an incident (docs/09-pdf-design.md).
 */
public class DigestTooLongException extends RuntimeException {

  public DigestTooLongException(int pages, int attempts) {
    super("Digest still has " + pages + " pages after " + attempts + " rendering attempts");
  }
}
