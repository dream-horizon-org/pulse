package org.dreamhorizon.pulseserver.service.notification.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationRetryPolicyTest {

  private NotificationConfig config;

  @BeforeEach
  void setUp() {
    config = new NotificationConfig();
    config.setRetry(new NotificationConfig.RetryConfig());
  }

  @Nested
  class ShouldRetry {

    @Test
    void shouldReturnFalseWhenPermanentFailure() {
      config.getRetry().setMaxAttempts(5);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      assertThat(policy.shouldRetry(0, true)).isFalse();
      assertThat(policy.shouldRetry(2, true)).isFalse();
      assertThat(policy.shouldRetry(5, true)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenAttemptCountBelowMax() {
      config.getRetry().setMaxAttempts(3);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      assertThat(policy.shouldRetry(0, false)).isTrue();
      assertThat(policy.shouldRetry(1, false)).isTrue();
      assertThat(policy.shouldRetry(2, false)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenAttemptCountEqualsOrExceedsMax() {
      config.getRetry().setMaxAttempts(3);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      assertThat(policy.shouldRetry(3, false)).isFalse();
      assertThat(policy.shouldRetry(4, false)).isFalse();
    }

    @Test
    void shouldUseDefaultMaxAttemptsWhenRetryConfigNull() {
      config.setRetry(null);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      // Default is 3 from NotificationConfig.getRetryConfig()
      assertThat(policy.shouldRetry(2, false)).isTrue();
      assertThat(policy.shouldRetry(3, false)).isFalse();
    }
  }

  @Nested
  class GetNextDelayMs {

    @Test
    void shouldReturnInitialDelayForZeroOrNegativeAttemptCount() {
      config.getRetry().setInitialDelayMs(1000);
      config.getRetry().setMultiplier(2.0);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      assertThat(policy.getNextDelayMs(0)).isEqualTo(1000);
      assertThat(policy.getNextDelayMs(-1)).isEqualTo(1000);
    }

    @Test
    void shouldExponentialBackoff() {
      config.getRetry().setInitialDelayMs(1000);
      config.getRetry().setMultiplier(2.0);
      config.getRetry().setMaxDelayMs(100000);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      assertThat(policy.getNextDelayMs(1)).isEqualTo(1000);   // 1000 * 2^0
      assertThat(policy.getNextDelayMs(2)).isEqualTo(2000);  // 1000 * 2^1
      assertThat(policy.getNextDelayMs(3)).isEqualTo(4000);  // 1000 * 2^2
      assertThat(policy.getNextDelayMs(4)).isEqualTo(8000);  // 1000 * 2^3
    }

    @Test
    void shouldCapAtMaxDelay() {
      config.getRetry().setInitialDelayMs(1000);
      config.getRetry().setMultiplier(2.0);
      config.getRetry().setMaxDelayMs(5000);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      assertThat(policy.getNextDelayMs(1)).isEqualTo(1000);
      assertThat(policy.getNextDelayMs(2)).isEqualTo(2000);
      assertThat(policy.getNextDelayMs(3)).isEqualTo(4000);
      assertThat(policy.getNextDelayMs(4)).isEqualTo(5000);  // capped at 5000
      assertThat(policy.getNextDelayMs(10)).isEqualTo(5000);
    }
  }

  @Nested
  class GetVisibilityTimeoutSeconds {

    @Test
    void shouldConvertDelayToSeconds() {
      config.getRetry().setInitialDelayMs(5000);
      config.getRetry().setMultiplier(1.0);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      // Minimum is 30 seconds per implementation
      assertThat(policy.getVisibilityTimeoutSeconds(0)).isGreaterThanOrEqualTo(30);
    }

    @Test
    void shouldReturnMinimumOf30Seconds() {
      config.getRetry().setInitialDelayMs(100);
      config.getRetry().setMultiplier(1.0);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      assertThat(policy.getVisibilityTimeoutSeconds(0)).isEqualTo(30);
    }

    @Test
    void shouldUseExponentialDelayForHigherAttempts() {
      config.getRetry().setInitialDelayMs(60000);
      config.getRetry().setMultiplier(2.0);
      config.getRetry().setMaxDelayMs(120000);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      assertThat(policy.getVisibilityTimeoutSeconds(0)).isEqualTo(60);
      assertThat(policy.getVisibilityTimeoutSeconds(1)).isEqualTo(60);
      assertThat(policy.getVisibilityTimeoutSeconds(2)).isEqualTo(120);
    }
  }

  @Nested
  class Getters {

    @Test
    void shouldExposeConfigValues() {
      config.getRetry().setMaxAttempts(5);
      config.getRetry().setInitialDelayMs(1000);
      config.getRetry().setMaxDelayMs(30000);
      config.getRetry().setMultiplier(2.5);
      NotificationRetryPolicy policy = new NotificationRetryPolicy(config);

      assertThat(policy.getMaxAttempts()).isEqualTo(5);
      assertThat(policy.getInitialDelayMs()).isEqualTo(1000);
      assertThat(policy.getMaxDelayMs()).isEqualTo(30000);
      assertThat(policy.getMultiplier()).isEqualTo(2.5);
    }
  }
}
