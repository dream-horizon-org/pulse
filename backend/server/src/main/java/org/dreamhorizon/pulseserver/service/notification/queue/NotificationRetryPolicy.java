package org.dreamhorizon.pulseserver.service.notification.queue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;

@Slf4j
@Getter
@Singleton
public class NotificationRetryPolicy {

  private final int maxAttempts;
  private final long initialDelayMs;
  private final long maxDelayMs;
  private final double multiplier;

  @Inject
  public NotificationRetryPolicy(NotificationConfig config) {
    NotificationConfig.RetryConfig retryConfig = config.getRetryConfig();
    this.maxAttempts = retryConfig.getMaxAttempts();
    this.initialDelayMs = retryConfig.getInitialDelayMs();
    this.maxDelayMs = retryConfig.getMaxDelayMs();
    this.multiplier = retryConfig.getMultiplier();

    log.info(
        "Retry policy configured: maxAttempts={}, initialDelayMs={}, maxDelayMs={}, multiplier={}",
        maxAttempts,
        initialDelayMs,
        maxDelayMs,
        multiplier);
  }

  public boolean shouldRetry(int attemptCount, boolean isPermanentFailure) {
    if (isPermanentFailure) {
      return false;
    }
    return attemptCount < maxAttempts;
  }

  public long getNextDelayMs(int attemptCount) {
    if (attemptCount <= 0) {
      return initialDelayMs;
    }

    long delay = (long) (initialDelayMs * Math.pow(multiplier, attemptCount - 1));
    return Math.min(delay, maxDelayMs);
  }

  public int getVisibilityTimeoutSeconds(int attemptCount) {
    long delayMs = getNextDelayMs(attemptCount);
    int timeoutSeconds = (int) Math.ceil(delayMs / 1000.0);
    return Math.max(timeoutSeconds, 30);
  }
}
