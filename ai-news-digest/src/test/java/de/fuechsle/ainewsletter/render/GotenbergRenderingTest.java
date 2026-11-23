package de.fuechsle.ainewsletter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.support.RenderAndMailContainers;
import de.fuechsle.ainewsletter.support.TestProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Renders the example digests with a real Gotenberg container and writes previews (HTML, PDF, PNG)
 * to {@code target/pdf-preview/} (docs/09-pdf-design.md, "Vorgehen"). The HTML preview can be
 * opened in a browser.
 */
class GotenbergRenderingTest {

  private static final Path PREVIEW = Path.of("target", "pdf-preview");

  private final DigestRenderer renderer = new DigestRenderer(RenderTestData.templateEngine());

  /**
   * The simple layout of step 8 fits short and normal digests on one page; the maximally long one
   * may take two pages until the final layout with the page check follows in roadmap step 17.
   */
  @ParameterizedTest
  @CsvSource({"short, 1", "normal, 1", "long, 2"})
  void exampleDigestBecomesAPdfWithItsTexts(String name, int maxPages) throws IOException {
    DigestView view = RenderTestData.view(name);
    String html = renderer.pdfHtml(view);

    byte[] pdf = client(RenderAndMailContainers.gotenbergUrl()).htmlToPdf(html);

    writePreview(name, html, pdf);
    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isBetween(1, maxPages);
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("Kurzfazit", "Key Takeaways");
      assertThat(text.replaceAll("\\s+", " ")).contains(view.sections().getFirst().heading());
    }
  }

  @Test
  void unreachableGotenbergIsAGotenbergException() {
    GotenbergClient client = client("http://127.0.0.1:9");

    assertThatThrownBy(() -> client.htmlToPdf("<html></html>"))
        .isInstanceOf(GotenbergException.class)
        .hasMessageStartingWith("PDF rendering with Gotenberg failed");
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
