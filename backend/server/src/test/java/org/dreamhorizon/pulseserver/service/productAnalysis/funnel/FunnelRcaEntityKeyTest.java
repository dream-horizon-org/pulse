package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

class FunnelRcaEntityKeyTest {

  @Test
  void shouldFormatEntityKey() {
    assertThat(FunnelRcaEntityKey.format(42L, 3)).isEqualTo("42:3");
  }

  @Test
  void shouldParseValidEntityKey() {
    FunnelRcaEntityKey.Parsed parsed = FunnelRcaEntityKey.parse(" 99 : 1 ");

    assertThat(parsed.funnelId()).isEqualTo(99L);
    assertThat(parsed.focusStepIndex()).isEqualTo(1);
  }

  @Test
  void shouldRejectBlankEntityKey() {
    assertThatThrownBy(() -> FunnelRcaEntityKey.parse("  "))
        .isInstanceOf(WebApplicationException.class);
  }

  @Test
  void shouldRejectMissingColon() {
    assertThatThrownBy(() -> FunnelRcaEntityKey.parse("123"))
        .isInstanceOf(WebApplicationException.class);
  }

  @Test
  void shouldRejectNonNumericParts() {
    assertThatThrownBy(() -> FunnelRcaEntityKey.parse("abc:1"))
        .isInstanceOf(WebApplicationException.class);
    assertThatThrownBy(() -> FunnelRcaEntityKey.parse("1:abc"))
        .isInstanceOf(WebApplicationException.class);
  }
}
