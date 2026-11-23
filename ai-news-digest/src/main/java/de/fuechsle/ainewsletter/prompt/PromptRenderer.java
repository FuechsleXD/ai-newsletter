package de.fuechsle.ainewsletter.prompt;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Replaces {@code {{name}}} placeholders in a prompt template (rules in
 * src/main/resources/prompts/README.md):
 *
 * <ul>
 *   <li>a placeholder without a value is an error (no empty replacement),
 *   <li>a value without a placeholder in the template is an error,
 *   <li>values are inserted literally in a single pass, so a value that itself contains {@code
 *       {{x}}} is not interpreted (no nested placeholders).
 * </ul>
 *
 * <p>Spring AI's template engine is deliberately not used because the JSON examples in the prompts
 * contain curly braces.
 */
@Component
public class PromptRenderer {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9]*)}}");

  public String render(PromptTemplate template, Map<String, String> values) {
    Set<String> placeholders = placeholders(template.text());

    Set<String> missing = new TreeSet<>(placeholders);
    missing.removeAll(values.keySet());
    if (!missing.isEmpty()) {
      throw new PromptException(describe(template) + ": no value for placeholder(s) " + missing);
    }
    Set<String> unused = new TreeSet<>(values.keySet());
    unused.removeAll(placeholders);
    if (!unused.isEmpty()) {
      throw new PromptException(describe(template) + ": unknown placeholder(s) " + unused);
    }

    Matcher matcher = PLACEHOLDER.matcher(template.text());
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String value = values.get(matcher.group(1));
      if (value == null) {
        throw new PromptException(
            describe(template) + ": value for placeholder " + matcher.group(1) + " is null");
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  static Set<String> placeholders(String text) {
    Set<String> names = new LinkedHashSet<>();
    Matcher matcher = PLACEHOLDER.matcher(text);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  private static String describe(PromptTemplate template) {
    return "Prompt " + template.name() + "/" + template.version();
  }
}
