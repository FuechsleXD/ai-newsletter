package de.fuechsle.ainewsletter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.fuechsle.ainewsletter.support.TestProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class OnePagePdfRendererTest {

  private final DigestRenderer renderer = mock(DigestRenderer.class);
  private final GotenbergClient gotenberg = mock(GotenbergClient.class);
  private final List<Double> factors = new ArrayList<>();
  private final DigestView view = RenderTestData.view("short");

  @Test
  void onePageOnTheFirstAttemptNeedsNoShortening() {
    when(renderer.pdfHtml(view)).thenReturn("<html>");
    when(gotenberg.htmlToPdf(anyString())).thenReturn(pdf(1));

    byte[] result = onePage(1).render(this::record);

    assertThat(PdfPages.count(result)).isEqualTo(1);
    assertThat(factors).containsExactly(1.0);
  }

  @Test
  void twoPagesAreRenderedAgainWithShorterTexts() {
    when(renderer.pdfHtml(view)).thenReturn("<html>");
    when(gotenberg.htmlToPdf(anyString())).thenReturn(pdf(2), pdf(1));

    assertThat(PdfPages.count(onePage(1).render(this::record))).isEqualTo(1);
    assertThat(factors).containsExactly(1.0, 0.7);
  }

  @Test
  void stillTooLongAfterTheLastAttemptFails() {
    when(renderer.pdfHtml(view)).thenReturn("<html>");
    when(gotenberg.htmlToPdf(anyString())).thenReturn(pdf(2));

    assertThatThrownBy(() -> onePage(2).render(this::record))
        .isInstanceOf(DigestTooLongException.class)
        .hasMessage("Digest still has 2 pages after 3 rendering attempts");
    assertThat(factors).hasSize(3);
    assertThat(factors.get(2)).isCloseTo(0.49, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void unreadablePdfIsAGotenbergError() {
    assertThatThrownBy(() -> PdfPages.count(new byte[] {1, 2, 3}))
        .isInstanceOf(GotenbergException.class)
        .hasMessageStartingWith("Gotenberg returned an unreadable PDF");
  }

  private DigestView record(double factor) {
    factors.add(factor);
    return view;
  }

  private OnePagePdfRenderer onePage(int shortenAttempts) {
    return new OnePagePdfRenderer(
        renderer,
        gotenberg,
        TestProperties.with(
            Map.of("newsdigest.render.shorten-attempts", String.valueOf(shortenAttempts))));
  }

  private static byte[] pdf(int pages) {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (int i = 0; i < pages; i++) {
        document.addPage(new PDPage());
      }
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
