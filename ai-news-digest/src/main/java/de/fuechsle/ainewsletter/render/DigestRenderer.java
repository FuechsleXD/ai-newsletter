package de.fuechsle.ainewsletter.render;

import de.fuechsle.ainewsletter.render.DigestView.KeyPointView;
import de.fuechsle.ainewsletter.render.DigestView.SectionView;
import de.fuechsle.ainewsletter.render.DigestView.SourceView;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders the three forms of a digest from the same {@link DigestView}: the HTML page for the PDF
 * ({@code templates/pdf/digest.html}), the HTML mail ({@code templates/mail/digest.html}) and the
 * plain text part of the mail. Thymeleaf escapes all texts of the HTML forms. Fixed texts and the
 * {@code lang} attribute follow the output language ({@link DigestLanguage}).
 */
@Component
public class DigestRenderer {

  static final String PDF_TEMPLATE = "pdf/digest";
  static final String MAIL_TEMPLATE = "mail/digest";

  private final ITemplateEngine templateEngine;
  private final DigestLanguage language;

  public DigestRenderer(ITemplateEngine templateEngine, DigestLanguage language) {
    this.templateEngine = templateEngine;
    this.language = language;
  }

  public String pdfHtml(DigestView digest) {
    return templateEngine.process(PDF_TEMPLATE, context(digest));
  }

  public String mailHtml(DigestView digest) {
    return templateEngine.process(MAIL_TEMPLATE, context(digest));
  }

  /** Plain text part for mail programs without HTML; same content and order as the HTML mail. */
  public String mailText(DigestView digest) {
    StringBuilder text = new StringBuilder();
    text.append(digest.title()).append('\n');
    text.append(digest.topicName()).append(" · ").append(digest.period()).append('\n');
    if (!digest.subtitle().isEmpty()) {
      text.append(digest.subtitle()).append('\n');
    }
    text.append(heading("digest.summary")).append(digest.summary()).append('\n');
    if (!digest.keyPoints().isEmpty()) {
      text.append(heading("digest.keyPoints"));
      for (KeyPointView point : digest.keyPoints()) {
        text.append("- ").append(point.text()).append('\n');
      }
    }
    for (SectionView section : digest.sections()) {
      text.append('\n').append(section.number()).append(". ").append(section.heading());
      text.append('\n').append(section.text()).append('\n');
      if (section.highlightValue() != null) {
        text.append(section.highlightValue())
            .append(' ')
            .append(section.highlightLabel())
            .append('\n');
      }
      if (!section.flowSteps().isEmpty()) {
        text.append(String.join(" -> ", section.flowSteps())).append('\n');
      }
      text.append(language.text("digest.sources"))
          .append(": ")
          .append(
              section.sourceNumbers().stream()
                  .map(number -> "[" + number + "]")
                  .collect(Collectors.joining(" ")))
          .append(" · ")
          .append(language.text("digest.models"))
          .append(": ")
          .append(String.join(", ", section.models()))
          .append(section.unconfirmed() ? " · " + language.text("digest.unconfirmed") : "")
          .append('\n');
    }
    if (!digest.takeaways().isEmpty()) {
      text.append(heading("digest.takeaways"));
      for (String takeaway : digest.takeaways()) {
        text.append("- ").append(takeaway).append('\n');
      }
    }
    if (!digest.watchOut().isEmpty()) {
      text.append(heading("digest.watchOut")).append(digest.watchOut()).append('\n');
    }
    text.append(heading("digest.sources"));
    for (SourceView source : digest.sources()) {
      text.append('[')
          .append(source.number())
          .append("] ")
          .append(source.title())
          .append(" - ")
          .append(source.url())
          .append('\n');
    }
    text.append('\n')
        .append(language.text("digest.createdOn", digest.createdOn()))
        .append(" · ")
        .append(language.text("digest.pdfAttached"))
        .append('\n');
    return text.toString();
  }

  /** Heading of a block of the plain text mail, e.g. a blank line and "SUMMARY". */
  private String heading(String key) {
    return "\n" + language.text(key).toUpperCase(language.locale()) + "\n";
  }

  private Context context(DigestView digest) {
    Context context = new Context(language.locale());
    context.setVariable("digest", digest);
    context.setVariable("lang", language.tag());
    return context;
  }
}
