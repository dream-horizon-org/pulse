package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClickhouseAnalyticsQueryUtilsTest {

  @Nested
  class ResolveGroupKey {

    @Test
    void shouldReturnUserIdForUniqueUsers() {
      assertThat(ClickhouseAnalyticsQueryUtils.resolveGroupKey("UNIQUE_USERS"))
          .isEqualTo("LogAttributes['user.id']");
    }

    @Test
    void shouldReturnSessionIdForSessions() {
      assertThat(ClickhouseAnalyticsQueryUtils.resolveGroupKey("SESSIONS"))
          .isEqualTo("LogAttributes['session.id']");
    }

    @Test
    void shouldBeCaseInsensitiveForSessions() {
      assertThat(ClickhouseAnalyticsQueryUtils.resolveGroupKey("sessions"))
          .isEqualTo("LogAttributes['session.id']");
    }

    @Test
    void shouldDefaultToUserIdForUnknownType() {
      assertThat(ClickhouseAnalyticsQueryUtils.resolveGroupKey("UNKNOWN"))
          .isEqualTo("LogAttributes['user.id']");
    }

    @Test
    void shouldDefaultToUserIdForNull() {
      assertThat(ClickhouseAnalyticsQueryUtils.resolveGroupKey(null))
          .isEqualTo("LogAttributes['user.id']");
    }
  }

  @Nested
  class ResolveStartExpr {

    @Test
    void shouldReturnIntervalExprForAutoMode() {
      String expr = ClickhouseAnalyticsQueryUtils.resolveStartExpr("AUTO", 7, null);
      assertThat(expr).isEqualTo("now() - INTERVAL 7 DAY");
    }

    @Test
    void shouldReturnIntervalExprForAutoModeWithStartTime() {
      Instant startTime = Instant.parse("2024-01-15T10:00:00Z");
      String expr = ClickhouseAnalyticsQueryUtils.resolveStartExpr("AUTO", 30, startTime);
      assertThat(expr).isEqualTo("now() - INTERVAL 30 DAY");
    }

    @Test
    void shouldReturnToDateTime64ForOnceMode() {
      Instant startTime = Instant.parse("2024-01-15T10:00:00Z");
      String expr = ClickhouseAnalyticsQueryUtils.resolveStartExpr("ONCE", 30, startTime);
      assertThat(expr).isEqualTo("toDateTime64('2024-01-15 10:00:00', 9)");
    }

    @Test
    void shouldFallbackToIntervalForOnceModeWithNullStartTime() {
      String expr = ClickhouseAnalyticsQueryUtils.resolveStartExpr("ONCE", 14, null);
      assertThat(expr).isEqualTo("now() - INTERVAL 14 DAY");
    }

    @Test
    void shouldBeCaseInsensitiveForOnceModeKeyword() {
      Instant startTime = Instant.parse("2024-06-01T00:00:00Z");
      String expr = ClickhouseAnalyticsQueryUtils.resolveStartExpr("once", 10, startTime);
      assertThat(expr).isEqualTo("toDateTime64('2024-06-01 00:00:00', 9)");
    }
  }

  @Nested
  class ResolveEndExpr {

    @Test
    void shouldReturnNowForAutoMode() {
      String expr = ClickhouseAnalyticsQueryUtils.resolveEndExpr("AUTO", null);
      assertThat(expr).isEqualTo("now()");
    }

    @Test
    void shouldReturnNowForAutoModeEvenWithEndTime() {
      Instant endTime = Instant.parse("2024-01-20T10:00:00Z");
      String expr = ClickhouseAnalyticsQueryUtils.resolveEndExpr("AUTO", endTime);
      assertThat(expr).isEqualTo("now()");
    }

    @Test
    void shouldReturnToDateTime64ForOnceMode() {
      Instant endTime = Instant.parse("2024-01-20T23:59:59Z");
      String expr = ClickhouseAnalyticsQueryUtils.resolveEndExpr("ONCE", endTime);
      assertThat(expr).isEqualTo("toDateTime64('2024-01-20 23:59:59', 9)");
    }

    @Test
    void shouldFallbackToNowForOnceModeWithNullEndTime() {
      String expr = ClickhouseAnalyticsQueryUtils.resolveEndExpr("ONCE", null);
      assertThat(expr).isEqualTo("now()");
    }
  }
}
