package de.fuechsle.ainewsletter.render;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.KeyPoint;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Section;
import de.fuechsle.ainewsletter.aggregate.DigestAggregate.Source;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

class DigestRendererTest {

  private static final String ATTACK = "<script>alert('x')</script><img src=x onerror=alert(1)>";

  private final DigestRenderer renderer = RenderTestData.renderer("en");

  @Test
  void mailContainsAllPartsInTheOrderOfThePdf() {
    DigestView view = RenderTestData.view("normal");

    Document mail = Jsoup.parse(renderer.mailHtml(view));

    String text = mail.body().text();
    assertThat(text)
        .contains("Java und KI im Überblick", "Summary", "Key points", "Key takeaways")
        .contains("What to watch", "PDF attached", "unconfirmed", "Sources: [1] [2]");
    assertThat(text.indexOf("Summary")).isLessThan(text.indexOf("1. Thema 1"));
    assertThat(text.indexOf("5. Thema 5")).isLessThan(text.indexOf("Key takeaways"));
    assertThat(mail.select("html").attr("lang")).isEqualTo("en");
    assertThat(text).contains("42 % schneller", "Ankündigung → Preview → Release");
  }

  @Test
  void mailIsSelfContainedWithoutScriptsOrExternalResources() {
    Document mail = Jsoup.parse(renderer.mailHtml(RenderTestData.view("normal")));

    assertThat(mail.select("script, link, img, iframe, style")).isEmpty();
    // Only the checked sources are linked
    assertThat(mail.select("a[href]"))
        .extracting(link -> link.attr("href"))
        .containsExactly(
            "https://news1.example.test/artikel/1",
            "https://news2.example.test/artikel/2",
            "https://news3.example.test/artikel/3",
            "https://news4.example.test/artikel/4",
            "https://news5.example.test/artikel/5",
            "https://news6.example.test/artikel/6");
  }

  @Test
  void textsFromTheModelsAreEscapedInMailAndPdf() {
    DigestView view = RenderTestData.view(attack());

    for (String html : List.of(renderer.mailHtml(view), renderer.pdfHtml(view))) {
      Document document = Jsoup.parse(html);
      assertThat(document.select("script")).isEmpty();
      assertThat(document.select("img")).isEmpty();
      assertThat(document.body().text()).contains("<script>alert('x')</script>");
      for (Element link : document.select("a[href]")) {
        assertThat(link.attr("href")).startsWith("https://");
      }
    }
  }

  @Test
  void pdfPageHasNoExternalResourcesAndUsesA4() {
    String html = renderer.pdfHtml(RenderTestData.view("normal"));
    Document pdf = Jsoup.parse(html);

    assertThat(pdf.select("script, link, img, iframe")).isEmpty();
    assertThat(pdf.select("style").html()).contains("@page { size: A4");
    assertThat(html).doesNotContain("url(", "@import");
    assertThat(pdf.select(".section")).hasSize(5);
  }

  @Test
  void plainTextPartHasTheSameContent() {
    String text = renderer.mailText(RenderTestData.view("normal"));

    assertThat(text)
        .startsWith("Java und KI im Überblick\nJava · Nov 16, 2026 – Nov 22, 2026\n")
        .contains("SUMMARY", "KEY POINTS", "KEY TAKEAWAYS", "WHAT TO WATCH", "SOURCES")
        .contains("1. Thema 1: Entwicklung im Überblick")
        .contains("Sources: [1] [2] · Models: Claude Test A · unconfirmed")
        .contains("Created on Nov 23, 2026 · PDF attached")
        .contains("42 % schneller", "Ankündigung -> Preview -> Release")
        .contains(
            "[6] Meldung 6: Neues Release mit Verbesserungen - https://news6.example.test/artikel/6");
  }

  @Test
  void shortDigestWithoutOptionalPartsRenders() {
    DigestView view = RenderTestData.view("short");

    String text = renderer.mailText(view);
    Document mail = Jsoup.parse(renderer.mailHtml(view));

    assertThat(text).doesNotContain("WHAT TO WATCH");
    assertThat(mail.body().text()).doesNotContain("What to watch");
    assertThat(Jsoup.parse(renderer.pdfHtml(view)).body().text()).doesNotContain("What to watch");
  }

  @Test
  void germanOutputUsesGermanTextsAndDates() {
    DigestLanguage german = RenderTestData.language("de");
    DigestRenderer germanRenderer = RenderTestData.renderer("de");
    DigestView view = RenderTestData.view(RenderTestData.aggregate("normal"), german);

    Document mail = Jsoup.parse(germanRenderer.mailHtml(view));
    Document pdf = Jsoup.parse(germanRenderer.pdfHtml(view));
    String text = germanRenderer.mailText(view);

    assertThat(mail.select("html").attr("lang")).isEqualTo("de");
    assertThat(pdf.select("html").attr("lang")).isEqualTo("de");
    assertThat(mail.body().text())
        .contains("Kurzfazit", "Kernpunkte", "Worauf achten", "PDF im Anhang", "unbestätigt");
    assertThat(pdf.body().text()).contains("Kurzfazit", "Erstellt am 23.11.2026", "Quellen");
    assertThat(text)
        .startsWith("Java und KI im Überblick\nJava · 16.11.2026 – 22.11.2026\n")
        .contains("KURZFAZIT", "WORAUF ACHTEN", "Quellen: [1] [2] · Modelle: Claude Test A");
  }

  @Test
  void countsInTheFooterUseSingularAndPlural() {
    DigestLanguage english = RenderTestData.language("en");

    assertThat(english.text("digest.modelCount", 1)).isEqualTo("1 model");
    assertThat(english.text("digest.sourceCount", 5)).isEqualTo("5 sources");
    assertThat(RenderTestData.language("de").text("digest.modelCount", 2)).isEqualTo("2 Modelle");
    // A language without its own file falls back to English, never to the server locale
    assertThat(RenderTestData.language("fr").text("digest.summary")).isEqualTo("Summary");
  }

  private static DigestAggregate attack() {
    return new DigestAggregate(
        ATTACK,
        ATTACK,
        ATTACK,
        List.of(new KeyPoint("ai", ATTACK)),
        List.of(
            new Section(
                ATTACK, "ai", ATTACK, List.of(ATTACK), null, List.of("1"), List.of(ATTACK))),
        List.of(ATTACK),
        ATTACK,
        List.of(new Source("1", ATTACK, "https://example.test/a?x=\"><script>")));
  }
}
