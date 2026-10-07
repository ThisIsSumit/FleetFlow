package com.fleetflow.account;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/** Development mail provider: writes the email (including links) to the log. Never use in production: the log would contain live tokens. */
@Component @ConditionalOnProperty(name = "fleetflow.mail.provider", havingValue = "mock", matchIfMissing = true)
public class MockMailService implements MailService {
  private static final Logger log = LoggerFactory.getLogger(MockMailService.class);
  public void send(String to, String subject, String body) { log.info("MOCK EMAIL to={} subject={}\n{}", to, subject, body); }
}
