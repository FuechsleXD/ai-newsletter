package de.fuechsle.ainewsletter.eval;

import de.fuechsle.ainewsletter.provider.SearchTrace;
import de.fuechsle.ainewsletter.research.UrlNormalizer;
import java.net.URI;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Markdown report of the searches of one research model ({@code searches-<model>.md} in the eval
 * export): every query with its hits, hits that became a news item are marked, plus the domains
 * that came back most often. Shows where news items get lost between search and answer.
 */
final class SearchTraceReport {

  static final String USED = "✅";
  private static final int TOP_DOMAINS = 15;

  private SearchTraceReport() {}

  /**
   * @param itemUrls URLs of the news items the model returned
   */
  static String markdown(String model, SearchTrace trace, Collection<String> itemUrls) {
    Set<String> used =
        itemUrls.stream().map(SearchTraceReport::normalize).collect(Collectors.toSet());
    long usedHits =
        trace.searches().stream()
            .flatMap(search -> search.hits().stream())
            .filter(hit -> used.contains(normalize(hit.url())))
            .count();

    StringBuilder text = new StringBuilder();
    text.append("# Suchen – ").append(model).append("\n\n");
    text.append("- Suchen: ").append(trace.searches().size()).append('\n');
    text.append("- Treffer: ").append(trace.hitCount()).append('\n');
    if (trace.otherToolCalls() > 0) {
      text.append("- Weitere Tool-Aufrufe ohne Suchbegriff (z. B. Code-Ausführung zum Filtern): ")
          .append(trace.otherToolCalls())
          .append('\n');
    }
    text.append("- Davon als Meldung übernommen (").append(USED).append("): ").append(usedHits);
    text.append(" von ").append(itemUrls.size()).append(" Meldungen\n");

    int number = 1;
    for (SearchTrace.Search search : trace.searches()) {
      text.append("\n## ").append(number++).append(". `").append(search.query()).append("`\n");
      if (search.errorCode() != null) {
        text.append("\nFehler des Suchwerkzeugs: `").append(search.errorCode()).append("`\n");
        continue;
      }
      if (search.hits().isEmpty()) {
        text.append("\nKeine Treffer\n");
        continue;
      }
      text.append('\n');
      for (SearchTrace.Hit hit : search.hits()) {
        text.append("- ")
            .append(used.contains(normalize(hit.url())) ? USED + " " : "")
            .append(hit.title())
            .append(" – ")
            .append(hit.url());
        if (hit.pageAge() != null) {
          text.append(" (").append(hit.pageAge()).append(')');
        }
        text.append('\n');
      }
    }

    Map<String, long[]> domains = new LinkedHashMap<>();
    trace.searches().stream()
        .flatMap(search -> search.hits().stream())
        .forEach(hit -> {
          long[] counts = domains.computeIfAbsent(domain(hit.url()), key -> new long[2]);
          counts[0]++;
          if (used.contains(normalize(hit.url()))) {
            counts[1]++;
          }
        });
    if (!domains.isEmpty()) {
      text.append("\n## Häufigste Domains\n\n| Domain | Treffer | übernommen |\n|---|---|---|\n");
      domains.entrySet().stream()
          .sorted(
              Comparator.comparingLong((Map.Entry<String, long[]> entry) -> entry.getValue()[0])
                  .reversed()
                  .thenComparing(Map.Entry::getKey))
          .limit(TOP_DOMAINS)
          .forEach(entry ->
              text.append("| ")
                  .append(entry.getKey())
                  .append(" | ")
                  .append(entry.getValue()[0])
                  .append(" | ")
                  .append(entry.getValue()[1])
                  .append(" |\n"));
    }
    return text.toString();
  }

  /** Host without "www.", or the URL itself if it cannot be parsed. */
  static String domain(String url) {
    try {
      String host = URI.create(url).getHost();
      if (host == null) {
        return url;
      }
      host = host.toLowerCase(Locale.ROOT);
      return host.startsWith("www.") ? host.substring(4) : host;
    } catch (IllegalArgumentException e) {
      return url;
    }
  }

  private static String normalize(String url) {
    try {
      return UrlNormalizer.normalize(url);
    } catch (RuntimeException e) {
      return url;
    }
  }
}
