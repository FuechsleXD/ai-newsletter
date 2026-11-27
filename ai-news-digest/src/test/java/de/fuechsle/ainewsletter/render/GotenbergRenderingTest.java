package de.fuechsle.ainewsletter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate;
import de.fuechsle.ainewsletter.support.RenderAndMailContainers;
import de.fuechsle.ainewsletter.support.TestProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Renders the example digests with a real Gotenberg container and writes previews (HTML, PDF, PNG)
 * to {@code target/pdf-preview/} (docs/09-pdf-design.md, "Vorgehen"). The HTML preview can be
 * opened in a browser; it needs nothing from the network.
 */
class GotenbergRenderingTest {

  private static final Path PREVIEW = Path.of("target", "pdf-preview");

  @ParameterizedTest
  @CsvSource({"short, en", "normal, en", "normal, de", "long, en"})
  void exampleDigestBecomesAOnePagePdfWithEmbeddedInter(String name, String language)
      throws IOException {
    DigestRenderer renderer = RenderTestData.renderer(language);
    DigestAggregate aggregate = RenderTestData.aggregate(name);
    DigestLanguage digestLanguage = RenderTestData.language(language);
    // HTML of the last attempt, the one that became the PDF
    String[] lastHtml = new String[1];
    OnePagePdfRenderer onePage = onePage(renderer, Map.of());

    byte[] pdf =
        onePage.render(factor -> {
          DigestView view = RenderTestData.view(aggregate, digestLanguage, factor);
          lastHtml[0] = renderer.pdfHtml(view);
          return view;
        });

    String preview = name + ("en".equals(language) ? "" : "-" + language);
    writePreview(preview, lastHtml[0], pdf);
    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isEqualTo(1);
      String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
      assertThat(text)
          .contains(digestLanguage.text("digest.summary"), digestLanguage.text("digest.takeaways"));
      assertThat(fonts(document))
          .isNotEmpty()
          .allSatisfy(font -> assertThat(font).contains("Inter"));
      assertThat(embedded(document)).isTrue();
    }
  }

  @Test
  void maximallyLongDigestNeedsTheShortenedSecondAttempt() throws IOException {
    DigestRenderer renderer = RenderTestData.renderer("en");
    byte[] full =
        client(RenderAndMailContainers.gotenbergUrl())
            .htmlToPdf(renderer.pdfHtml(RenderTestData.view("long")));

    assertThat(PdfPages.count(full)).isGreaterThan(1);
    // Without a shortening attempt it does not fit
    OnePagePdfRenderer noShortening =
        onePage(renderer, Map.of("newsdigest.render.shorten-attempts", "0"));
    assertThatThrownBy(() -> noShortening.render(factor -> RenderTestData.view("long")))
        .isInstanceOf(DigestTooLongException.class)
        .hasMessageContaining("after 1 rendering attempts");
  }

  @Test
  void unreachableGotenbergIsAGotenbergException() {
    GotenbergClient client = client("http://127.0.0.1:9");

    assertThatThrownBy(() -> client.htmlToPdf("<html></html>"))
        .isInstanceOf(GotenbergException.class)
        .hasMessageStartingWith("PDF rendering with Gotenberg failed");
  }

  private static OnePagePdfRenderer onePage(DigestRenderer renderer, Map<String, String> values) {
    Map<String, String> all = new HashMap<>(values);
    all.put("newsdigest.render.gotenberg-url", RenderAndMailContainers.gotenbergUrl());
    all.put("newsdigest.render.gotenberg-timeout", "20s");
    return new OnePagePdfRenderer(
        renderer, client(RenderAndMailContainers.gotenbergUrl()), TestProperties.with(all));
  }

  private static GotenbergClient client(String url) {
    return new GotenbergClient(
        TestProperties.with(
            Map.of(
                "newsdigest.render.gotenberg-url",
                url,
                "newsdigest.render.gotenberg-timeout",
                "20s")));
  }

  /** Names of the fonts used on the first page. */
  private static Set<String> fonts(PDDocument document) throws IOException {
    Set<String> names = new HashSet<>();
    PDResources resources = document.getPage(0).getResources();
    for (COSName name : resources.getFontNames()) {
      names.add(resources.getFont(name).getName());
    }
    return names;
  }

  private static boolean embedded(PDDocument document) throws IOException {
    PDResources resources = document.getPage(0).getResources();
    for (COSName name : resources.getFontNames()) {
      PDFont font = resources.getFont(name);
      if (!font.isEmbedded()) {
        return false;
      }
    }
    return true;
  }

  private static void writePreview(String name, String html, byte[] pdf) {
    try {
      Files.createDirectories(PREVIEW);
      Files.writeString(PREVIEW.resolve(name + ".html"), html, StandardCharsets.UTF_8);
      Files.write(PREVIEW.resolve(name + ".pdf"), pdf);
      try (PDDocument document = Loader.loadPDF(pdf)) {
        BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 96);
        ImageIO.write(image, "png", PREVIEW.resolve(name + ".png").toFile());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
