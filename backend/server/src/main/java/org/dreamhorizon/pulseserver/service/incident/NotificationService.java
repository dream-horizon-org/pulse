package org.dreamhorizon.pulseserver.service.incident;

import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;

/**
 * Placeholder notification service.
 * The actual email and Slack implementations will be provided by the peer library.
 * Replace the placeholder bodies below with real calls once the library is available.
 */
@Slf4j
@Singleton
public class NotificationService {

  /**
   * Sends an email notification.
   * TODO: replace with peer library call — e.g. EmailClient.sendEmail(to, subject, body)
   */
  public Completable sendEmail(String to, String subject, String body) {
    log.info("[PLACEHOLDER] sendEmail to={} subject={}", to, subject);
    return Completable.complete();
  }

  /**
   * Sends a Slack message to the internal channel.
   * TODO: replace with peer library call — e.g. SlackClient.sendMessage(channel, message)
   */
  public Completable sendSlackMessage(String channel, String message) {
    log.info("[PLACEHOLDER] sendSlackMessage channel={}", channel);
    return Completable.complete();
  }
}
