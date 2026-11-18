package com.fleetflow.account;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
public interface MailService {
  void send(String to, String subject, String body);
  /** Sends once the surrounding transaction has committed (immediately when there is none), so a slow provider never holds
   *  database locks and the mail never carries a token that was rolled back. Delivery errors are always swallowed and logged:
   *  a failure must not fail the request, or reveal (e.g. in forgot-password) that an account exists. */
  default void sendAfterCommit(String to, String subject, String body) {
    Runnable task = () -> {
      try { send(to, subject, body); }
      catch (Exception e) { LoggerFactory.getLogger(MailService.class).error("Email delivery failed (recipient domain {}): {}", to.contains("@") ? to.substring(to.indexOf('@') + 1) : "?", e.toString()); }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { task.run(); } });
    else task.run();
  }
}
