package de.fuechsle.ainewsletter.render;

/**
 * Shortens texts to a maximum length before rendering (docs/09-pdf-design.md, "Stilregeln"): cut at
 * the last word boundary that fits and end with an ellipsis, so the layout keeps its size.
 */
public final class TextShortener {

  static final String ELLIPSIS = "…";

  private TextShortener() {}

  /**
   * @param maxChars maximum length including the ellipsis
   * @return the stripped text, shortened if it is longer than {@code maxChars}; {@code ""} for
   *     {@code null}
   */
  public static String shorten(String text, int maxChars) {
    if (maxChars < 2) {
      throw new IllegalArgumentException("maxChars must be at least 2");
    }
    if (text == null) {
      return "";
    }
    String value = text.strip();
    if (value.length() <= maxChars) {
      return value;
    }
    int limit = maxChars - ELLIPSIS.length();
    int cut = value.lastIndexOf(' ', limit);
    String head = cut > limit / 2 ? value.substring(0, cut) : value.substring(0, limit);
    return head.stripTrailing() + ELLIPSIS;
  }
}
