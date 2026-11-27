package de.fuechsle.ainewsletter.render;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Page count of a rendered PDF (PDFBox). */
final class PdfPages {

  private PdfPages() {}

  static int count(byte[] pdf) {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      return document.getNumberOfPages();
    } catch (IOException e) {
      throw new GotenbergException("Gotenberg returned an unreadable PDF: " + e.getMessage(), e);
    }
  }
}
