package com.fleetflow.account;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;
/** Real SMTP delivery (multipart: plain text + branded HTML). Point MAIL_HOST/PORT/USER/PASSWORD at any SMTP provider (SES, SendGrid, Mailgun, Postfix...).
 *  Delivery failures are logged and swallowed: signup and password-reset must not fail (or reveal account existence) because email is down; users can resend. */
@Component @ConditionalOnProperty(name = "fleetflow.mail.provider", havingValue = "smtp")
public class SmtpMailService implements MailService {
  private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);
  private final JavaMailSender sender; private final String from;
  public SmtpMailService(JavaMailSender sender, @Value("${fleetflow.mail.from}") String from) { this.sender = sender; this.from = from; }
  @Override public void send(String to, String subject, String body) {
    try {
      MimeMessage m = sender.createMimeMessage(); var h = new MimeMessageHelper(m, true, "UTF-8");
      h.setFrom(from); h.setTo(to); h.setSubject(subject); h.setText(body, EmailHtml.render(subject, body)); sender.send(m);
    } catch (Exception e) { log.error("Email delivery failed (recipient domain {}): {}", to.contains("@") ? to.substring(to.indexOf('@') + 1) : "?", e.toString()); }
  }
}
