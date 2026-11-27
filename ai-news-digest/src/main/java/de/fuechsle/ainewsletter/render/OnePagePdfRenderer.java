package de.fuechsle.ainewsletter.render;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.util.function.DoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renders the PDF of a digest so that it has exactly one page (docs/09-pdf-design.md): the first
 * attempt uses the configured length limits; if the PDF has more pages, it is rendered again with
 * shorter texts, at most {@code newsdigest.render.shorten-attempts} times, each keeping {@value
 * #SHORTEN_FACTOR} of the previous limits. Then the job fails.
 */
@Component
public class OnePagePdfRenderer {

  /** Each further attempt keeps this share of the length limits. */
  static final double SHORTEN_FACTOR = 0.7;

  private static final Logger log = LoggerFactory.getLogger(OnePagePdfRenderer.class);

  private final DigestRenderer renderer;
  private final GotenbergClient gotenberg;
  private final int shortenAttempts;

  public OnePagePdfRenderer(
      DigestRenderer renderer, GotenbergClient gotenberg, NewsDigestProperties properties) {
    this.renderer = renderer;
    this.gotenberg = gotenberg;
    this.shortenAttempts = properties.render().shortenAttempts();
  }

  /**
   * @param viewWithLengthFactor the digest with its length limits scaled by the given factor
   * @return the one-page PDF
   * @throws DigestTooLongException if the last attempt still has more than one page
   */
  public byte[] render(DoubleFunction<DigestView> viewWithLengthFactor) {
    int pages = 0;
    for (int attempt = 0; attempt <= shortenAttempts; attempt++) {
      byte[] pdf =
          gotenberg.htmlToPdf(renderer.pdfHtml(viewWithLengthFactor.apply(lengthFactor(attempt))));
      pages = PdfPages.count(pdf);
      if (pages == 1) {
        if (attempt > 0) {
          log.info("Digest fits on one page after shortening (attempt {})", attempt + 1);
        }
        return pdf;
      }
      log.info("Digest has {} pages (attempt {})", pages, attempt + 1);
    }
    throw new DigestTooLongException(pages, shortenAttempts + 1);
  }

  /** Length factor of an attempt: 1.0, then 0.7, 0.49 ... */
  static double lengthFactor(int attempt) {
    return Math.pow(SHORTEN_FACTOR, attempt);
  }
}
