package de.fuechsle.ainewsletter.eval;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.provider.SearchTrace;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTraceReportTest {

  @Test
  void showsEveryQueryWithItsHitsAndMarksTheUsedOnes() {
    SearchTrace trace =
        new SearchTrace(
            List.of(
                new SearchTrace.Search(
                    "Spring AI November 2026",
                    List.of(
                        new SearchTrace.Hit(
                            "Spring AI 2.1", "https://spring.example.test/blog/2-1/", "1 day ago"),
                        new SearchTrace.Hit("Digest", "https://www.digest.example.test/w", null)),
                    null),
                new SearchTrace.Search("Copilot", List.of(), "max_uses_exceeded"),
                new SearchTrace.Search("leer", List.of(), null)),
            4);

    // The item URL differs only in normalization (fragment, trailing slash)
    String report =
        SearchTraceReport.markdown(
            "Claude Test A", trace, List.of("https://spring.example.test/blog/2-1#top"));

    assertThat(report)
        .startsWith("# Suchen – Claude Test A")
        .contains(
            "- Suchen: 3",
            "- Treffer: 2",
            "ohne Suchbegriff (z. B. Code-Ausführung zum Filtern): 4",
            "übernommen (✅): 1 von 1 Meldungen")
        .contains("## 1. `Spring AI November 2026`")
        .contains("- ✅ Spring AI 2.1 – https://spring.example.test/blog/2-1/ (1 day ago)")
        .contains("- Digest – https://www.digest.example.test/w")
        .contains("## 2. `Copilot`", "Fehler des Suchwerkzeugs: `max_uses_exceeded`")
        .contains("## 3. `leer`", "Keine Treffer")
        .contains("| spring.example.test | 1 | 1 |", "| digest.example.test | 1 | 0 |");
  }

  @Test
  void domainWithoutWwwAndFallbackForInvalidUrls() {
    assertThat(SearchTraceReport.domain("https://WWW.Example.TEST/a")).isEqualTo("example.test");
    assertThat(SearchTraceReport.domain("not a url")).isEqualTo("not a url");
  }
}
