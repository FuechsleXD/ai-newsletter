package de.fuechsle.ainewsletter.render;

import de.fuechsle.ainewsletter.aggregate.IconKeys;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Fonts and icons of the PDF, embedded in the HTML page (docs/09-pdf-design.md): Inter as base64
 * {@code @font-face} rules ({@code pdf/fonts/}, SIL Open Font License) and Lucide icons as inline
 * SVG ({@code pdf/icons/}, ISC license). Gotenberg receives a single self-contained page and loads
 * nothing; the HTML preview works offline in a browser.
 *
 * <p>Every allowed icon key needs its SVG ({@code pdf/icons/<key>.svg}); a missing file stops the
 * start of the app. The SVGs are our own files, never text from a model, so they are inserted
 * unescaped.
 */
@Component
public class DigestAssets {

  static final List<String> FONT_WEIGHTS =
      List.of("Regular:400", "SemiBold:600", "Bold:700", "ExtraBold:800");

  /** Icons of the fixed parts of the layout ({@code pdf/icons/ui/}). */
  static final List<String> UI_ICONS =
      List.of("check", "eye", "file-text", "list-checks", "newspaper");

  private final String fontFaces;
  private final Map<String, String> icons = new HashMap<>();
  private final Map<String, String> uiIcons = new HashMap<>();

  public DigestAssets(IconKeys iconKeys) {
    StringBuilder css = new StringBuilder();
    for (String weight : FONT_WEIGHTS) {
      String[] parts = weight.split(":");
      css.append("@font-face{font-family:\"Inter\";font-style:normal;font-weight:")
          .append(parts[1])
          .append(";src:url(data:font/woff2;base64,")
          .append(
              Base64.getEncoder().encodeToString(read("pdf/fonts/Inter-" + parts[0] + ".woff2")))
          .append(") format(\"woff2\");}\n");
    }
    this.fontFaces = css.toString();
    for (String key : iconKeys.keys()) {
      icons.put(key, svg("pdf/icons/" + key + ".svg"));
    }
    for (String name : UI_ICONS) {
      uiIcons.put(name, svg("pdf/icons/ui/" + name + ".svg"));
    }
  }

  /** {@code @font-face} rules with the embedded fonts. */
  public String fontFaces() {
    return fontFaces;
  }

  /** Inline SVG of an icon key; unknown keys get the {@code default} icon. */
  public String icon(String key) {
    String svg = icons.get(key);
    return svg != null ? svg : icons.get(IconKeys.DEFAULT);
  }

  /** Inline SVG of an icon of the layout, e.g. {@code check}. */
  public String ui(String name) {
    String svg = uiIcons.get(name);
    if (svg == null) {
      throw new IllegalArgumentException("Unknown layout icon: " + name);
    }
    return svg;
  }

  private static String svg(String location) {
    String svg = new String(read(location), StandardCharsets.UTF_8).strip();
    if (!svg.startsWith("<svg") || svg.contains("<script") || svg.contains("href")) {
      throw new IllegalStateException("Unexpected content in " + location);
    }
    // One line, with the layout deciding the size (CSS) instead of width/height attributes
    return svg.replaceAll("\\s+", " ")
        .replaceFirst(" width=\"24\"", "")
        .replaceFirst(" height=\"24\"", "")
        .replaceFirst("<svg ", "<svg aria-hidden=\"true\" ");
  }

  private static byte[] read(String location) {
    try (InputStream in = new ClassPathResource(location).getInputStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Missing digest asset " + location, e);
    }
  }
}
