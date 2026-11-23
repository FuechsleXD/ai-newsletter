package de.fuechsle.ainewsletter.provider;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the JSON object of a model answer. Text around the object (e.g. a sentence before it or a
 * Markdown code fence) is ignored, because models often add it despite the prompt.
 */
public final class JsonAnswer {

  private JsonAnswer() {}

  /**
   * @return the JSON object from the first <code>{</code> to the last <code>}</code>
   * @throws AnswerParseException if the answer is empty, contains no object or the object is not
   *     valid JSON
   */
  public static JsonNode readObject(ObjectMapper objectMapper, String text) {
    if (text == null) {
      throw new AnswerParseException("Answer is empty");
    }
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end < start) {
      throw new AnswerParseException("Answer contains no JSON object");
    }
    try {
      return objectMapper.readTree(text.substring(start, end + 1));
    } catch (JacksonException e) {
      throw new AnswerParseException("Invalid JSON: " + e.getOriginalMessage());
    }
  }

  /** Trimmed text of a string field; {@code null} if missing, not a string or blank. */
  public static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString()) {
      return null;
    }
    String result = value.asString().strip();
    return result.isEmpty() ? null : result;
  }
}
