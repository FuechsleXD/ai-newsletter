package de.fuechsle.ainewsletter.render;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Language of the digest (PDF, mail, subject) from {@code newsdigest.output.language} (ADR-017):
 * the fixed texts come from {@code messages/digest*.properties}, dates are formatted for the
 * locale. Operator notifications stay German (UI texts, golden rule 13).
 */
@Component
public class DigestLanguage {

  /** Base name of the message files of the digest. */
  public static final String MESSAGES = "messages/digest";

  private final Locale locale;
  private final DateTimeFormatter dateFormat;
  private final MessageSource messages;

  public DigestLanguage(NewsDigestProperties properties, MessageSource messages) {
    this.locale = properties.output().language();
    this.dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
    this.messages = messages;
  }

  /** Message source for the digest texts outside of Spring (tests, tools). */
  public static MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename(MESSAGES);
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }

  public Locale locale() {
    return locale;
  }

  /** Language tag for the {@code lang} attribute of the HTML, e.g. {@code en}. */
  public String tag() {
    return locale.toLanguageTag();
  }

  /** Date in the medium format of the locale, e.g. "Nov 22, 2026" or "22.11.2026". */
  public String date(LocalDate date) {
    return dateFormat.format(date);
  }

  /** Fixed text of the digest, e.g. {@code digest.summary}. */
  public String text(String key, Object... arguments) {
    return messages.getMessage(key, arguments, locale);
  }
}
