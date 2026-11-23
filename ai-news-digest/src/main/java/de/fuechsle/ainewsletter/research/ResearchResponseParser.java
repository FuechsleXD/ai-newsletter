package de.fuechsle.ainewsletter.research;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses the research answer of a model into {@link ResearchResponse}.
 *
 * <p>Structure errors (no JSON object, no {@code items} array) raise a {@link
 * ResearchParseException}, which leads to one repair attempt. Single items that break the contract
 * (missing headline or summary, no absolute http(s) URL, URL too long) are dropped and counted, so
 * one bad item does not discard the whole answer. A publication date that is not {@code YYYY-MM-DD}
 * counts as unknown. Text around the JSON object (e.g. a sentence before it or a Markdown code
 * fence) is ignored, because models with web search often add it despite the prompt.
 */
@Component
public class ResearchResponseParser {

  static final int MAX_URL_LENGTH = 2048;

  private final ObjectMapper objectMapper;

  public ResearchResponseParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * @param maxItems maximum number of items; further items are dropped (the model sorts by
   *     importance)
   */
  public ResearchResponse parse(String text, int maxItems) {
    JsonNode root = readObject(text);
    JsonNode items = root.get("items");
    if (items == null || !items.isArray()) {
      throw new ResearchParseException("JSON object has no array \"items\"");
    }

    List<NewsItemCandidate> candidates = new ArrayList<>();
    int skipped = 0;
    for (JsonNode item : items) {
      NewsItemCandidate candidate = toCandidate(item);
      if (candidate == null || candidates.size() >= maxItems) {
        skipped++;
      } else {
        candidates.add(candidate);
      }
    }
    return new ResearchResponse(candidates, skipped);
  }

  private JsonNode readObject(String text) {
    if (text == null) {
      throw new ResearchParseException("Answer is empty");
    }
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end < start) {
      throw new ResearchParseException("Answer contains no JSON object");
    }
    try {
      return objectMapper.readTree(text.substring(start, end + 1));
    } catch (JacksonException e) {
      throw new ResearchParseException("Invalid JSON: " + e.getOriginalMessage(), e);
    }
  }

  private static NewsItemCandidate toCandidate(JsonNode item) {
    if (!item.isObject()) {
      return null;
    }
    String headline = text(item, "headline");
    String summary = text(item, "summary");
    String url = text(item, "url");
    if (headline == null || summary == null || !isHttpUrl(url)) {
      return null;
    }
    return new NewsItemCandidate(headline, summary, url, date(text(item, "publishedAt")));
  }

  private static String text(JsonNode item, String field) {
    JsonNode node = item.get(field);
    if (node == null || !node.isString()) {
      return null;
    }
    String value = node.asString().strip();
    return value.isEmpty() ? null : value;
  }

  private static boolean isHttpUrl(String url) {
    if (url == null || url.length() > MAX_URL_LENGTH) {
      return false;
    }
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static LocalDate date(String value) {
    if (value == null) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
