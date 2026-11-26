package de.fuechsle.ainewsletter.prompt;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A versioned prompt template, e.g. {@code research/v1}.
 *
 * @param name prompt name (folder under {@code prompts/})
 * @param version version (file name without {@code .txt}), stored in {@code digest_run}
 * @param text template text with {@code {{placeholder}}} markers
 */
public record PromptTemplate(String name, String version, String text) {

  /**
   * Headings of the section that describes the expected answer format: {@code OUTPUT} in the
   * English templates, {@code AUSGABE} in the older German ones.
   */
  static final List<String> OUTPUT_SECTIONS = List.of("OUTPUT", "AUSGABE");

  /**
   * The output section of the template (from the line {@code OUTPUT} or {@code AUSGABE} to the
   * end), shown again in the repair prompt; the whole text if the template has no such section.
   */
  public String outputSection() {
    for (String heading : OUTPUT_SECTIONS) {
      Matcher matcher = Pattern.compile("(?m)^" + heading + "$").matcher(text);
      if (matcher.find()) {
        return text.substring(matcher.start()).strip();
      }
    }
    return text;
  }
}
