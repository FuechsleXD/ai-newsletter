package de.fuechsle.ainewsletter.prompt;

/**
 * A versioned prompt template, e.g. {@code research/v1}.
 *
 * @param name prompt name (folder under {@code prompts/})
 * @param version version (file name without {@code .txt}), stored in {@code digest_run}
 * @param text template text with {@code {{placeholder}}} markers
 */
public record PromptTemplate(String name, String version, String text) {

  /** Heading of the section that describes the expected answer format. */
  static final String OUTPUT_SECTION = "AUSGABE";

  /**
   * The output section of the template (from {@code AUSGABE} to the end), shown again in the repair
   * prompt; the whole text if the template has no such section.
   */
  public String outputSection() {
    int index = text.indexOf(OUTPUT_SECTION);
    return index < 0 ? text : text.substring(index).strip();
  }
}
