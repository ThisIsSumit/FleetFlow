package com.fleetflow.account;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "fleetflow.mail.provider",
        havingValue = "resend"
)
public class ResendMailService implements MailService {

    private static final Logger log =
            LoggerFactory.getLogger(ResendMailService.class);

    private final Resend resend;
    private final String from;

    public ResendMailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${fleetflow.mail.from}") String from
    ) {
        this.resend = new Resend(apiKey);
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {

        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .html(EmailHtml.render(subject, body))
                    .build();

            resend.emails().send(params);

            log.info(
                    "Email sent successfully to recipient domain {}",
                    to.contains("@")
                            ? to.substring(to.indexOf('@') + 1)
                            : "?"
            );

        } catch (ResendException e) {

            log.error(
                    "Resend email delivery failed (recipient domain {}): {}",
                    to.contains("@")
                            ? to.substring(to.indexOf('@') + 1)
                            : "?",
                    e.getMessage()
            );
        }
    }
}