package de.fuechsle.ainewsletter.aggregate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The fixed list of icon keys the aggregator may choose from ({@code pdf/icon-keys.txt}). The
 * aggregator only picks a key; the icons themselves are local files (docs/09-pdf-design.md).
 */
@Component
public class IconKeys {

  /** Fallback for unknown or missing keys; always part of the list. */
  public static final String DEFAULT = "default";

  static final String LOCATION = "pdf/icon-keys.txt";

  private final List<String> keys;

  public IconKeys() {
    this(read(LOCATION));
  }

  public IconKeys(List<String> keys) {
    if (!keys.contains(DEFAULT)) {
      throw new IllegalStateException("Icon key list must contain \"" + DEFAULT + "\"");
    }
    this.keys = List.copyOf(keys);
  }

  public List<String> keys() {
    return keys;
  }

  /** The key itself if it is allowed, otherwise {@link #DEFAULT}. */
  public String orDefault(String key) {
    return key != null && keys.contains(key) ? key : DEFAULT;
  }

  private static List<String> read(String location) {
    try (InputStream in = new ClassPathResource(location).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .map(String::strip)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .distinct()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Icon key list " + location + " cannot be read", e);
    }
  }
}
