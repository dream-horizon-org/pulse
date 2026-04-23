package org.dreamhorizon.pulsealertscron.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DashboardUrlResolverTest {

  @Nested
  class Resolve {
    @Test
    void shouldUseFallbackWhenNull() {
      assertThat(DashboardUrlResolver.resolve(null))
          .isEqualTo(DashboardUrlResolver.FALLBACK_DASHBOARD_URL);
    }

    @Test
    void shouldUseFallbackWhenBlank() {
      assertThat(DashboardUrlResolver.resolve("   "))
          .isEqualTo(DashboardUrlResolver.FALLBACK_DASHBOARD_URL);
    }

    @Test
    void shouldTrimAndStripTrailingSlashes() {
      assertThat(DashboardUrlResolver.resolve(" http://localhost:3000/// "))
          .isEqualTo("http://localhost:3000");
    }

    @Test
    void shouldPreserveUrlWithoutTrailingSlash() {
      assertThat(DashboardUrlResolver.resolve("https://app.example.com"))
          .isEqualTo("https://app.example.com");
    }
  }
}
