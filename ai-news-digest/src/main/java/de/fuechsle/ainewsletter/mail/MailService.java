package de.fuechsle.ainewsletter.mail;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends mails over SMTP with {@link JavaMailSender} (locally Mailpit, in AWS SES); one code path
 * for all environments. The sender address comes only from the environment ({@code
 * NEWSDIGEST_MAIL_FROM}). Every mail goes to exactly one recipient, so recipients never see each
 * other.
 */
@Component
public class MailService {

  static final String PDF_FILE_NAME = "news-digest.pdf";

  private final JavaMailSender mailSender;
  private final String from;

  public MailService(JavaMailSender mailSender, NewsDigestProperties properties) {
    this.mailSender = mailSender;
    this.from = properties.mail().from();
  }

  /**
   * Digest mail: HTML with plain text alternative and the PDF as attachment.
   *
   * @throws MailException if the mail cannot be sent
   */
  public void sendDigest(String to, String subject, String html, String text, byte[] pdf) {
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(text, html);
      helper.addAttachment(PDF_FILE_NAME, new ByteArrayResource(pdf), "application/pdf");
    } catch (MessagingException e) {
      throw new MailPreparationException(e);
    }
    mailSender.send(message);
  }

  /**
   * Plain text mail, e.g. to the operator.
   *
   * @throws MailException if the mail cannot be sent
   */
  public void sendText(String to, String subject, String text) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);
    mailSender.send(message);
  }
}
