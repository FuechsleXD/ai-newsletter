package de.fuechsle.ainewsletter.research;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalizes URLs for the duplicate checks (docs/02-architektur.md, "URL-Normalisierung"): scheme
 * and host in lower case, without default port, without fragment, without tracking parameters
 * ({@code utm_*} and well-known click IDs) and without trailing slash. Path and remaining query
 * parameters keep their case and order, because servers may treat them case-sensitively.
 */
public final class UrlNormalizer {

  private static final Set<String> TRACKING_PARAMETERS =
      Set.of(
          "fbclid",
          "gclid",
          "dclid",
          "gbraid",
          "wbraid",
          "msclkid",
          "yclid",
          "twclid",
          "igshid",
          "mc_cid",
          "mc_eid",
          "_ga",
          "_gl",
          "_hsenc",
          "_hsmi",
          "mkt_tok",
          "oly_anon_id",
          "oly_enc_id",
          "vero_id",
          "wickedid");

  private UrlNormalizer() {}

  /**
   * @return the normalized URL; the input unchanged (stripped) if it is not a valid absolute URL
   */
  public static String normalize(String url) {
    String value = url.strip();
    URI uri;
    try {
      uri = new URI(value);
    } catch (URISyntaxException e) {
      return value;
    }
    if (uri.getScheme() == null || uri.getRawAuthority() == null || uri.getHost() == null) {
      return value;
    }

    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    StringBuilder result = new StringBuilder(scheme).append("://");
    if (uri.getRawUserInfo() != null) {
      result.append(uri.getRawUserInfo()).append('@');
    }
    result.append(uri.getHost().toLowerCase(Locale.ROOT));
    if (uri.getPort() >= 0 && !isDefaultPort(scheme, uri.getPort())) {
      result.append(':').append(uri.getPort());
    }

    String path = uri.getRawPath() == null ? "" : uri.getRawPath();
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    result.append(path);

    String query = withoutTracking(uri.getRawQuery());
    if (!query.isEmpty()) {
      result.append('?').append(query);
    }
    return result.toString();
  }

  private static boolean isDefaultPort(String scheme, int port) {
    return (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
  }

  private static String withoutTracking(String rawQuery) {
    if (rawQuery == null || rawQuery.isEmpty()) {
      return "";
    }
    return Arrays.stream(rawQuery.split("&"))
        .filter(parameter -> !parameter.isEmpty())
        .filter(parameter -> !isTracking(parameter))
        .collect(Collectors.joining("&"));
  }

  private static boolean isTracking(String parameter) {
    int separator = parameter.indexOf('=');
    String name =
        (separator < 0 ? parameter : parameter.substring(0, separator)).toLowerCase(Locale.ROOT);
    return name.startsWith("utm_") || TRACKING_PARAMETERS.contains(name);
  }
}
