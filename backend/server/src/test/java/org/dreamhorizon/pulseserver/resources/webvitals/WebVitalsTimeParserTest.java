package org.dreamhorizon.pulseserver.resources.webvitals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WebVitalsTimeParser")
class WebVitalsTimeParserTest {

  private static final Instant KNOWN = Instant.parse("2026-05-01T00:00:00Z");

  @Nested
  @DisplayName("parseQueryInstant")
  class ParseQueryInstant {

    @Test
    @DisplayName("should_parse_epoch_milliseconds_matching_dashboard_query_strings")
    void shouldParseEpochMilliseconds() {
      String ms = String.valueOf(KNOWN.toEpochMilli());
      assertThat(WebVitalsTimeParser.parseQueryInstant(ms, "startTime")).isEqualTo(KNOWN);
    }

    @Test
    @DisplayName("should_parse_ISO_instant_strings")
    void shouldParseIsoInstantStrings() {
      assertThat(WebVitalsTimeParser.parseQueryInstant("2026-05-01T00:00:00Z", "endTime"))
          .isEqualTo(KNOWN);
    }

    @Test
    @DisplayName("should_trim_whitespace_before_parsing")
    void shouldTrimWhitespace() {
      assertThat(WebVitalsTimeParser.parseQueryInstant("  " + KNOWN.toEpochMilli() + "  ", "startTime"))
          .isEqualTo(KNOWN);
    }

    @Test
    @DisplayName("should_throw_400_when_value_blank")
    void shouldRejectBlank() {
      assertThatThrownBy(() -> WebVitalsTimeParser.parseQueryInstant("", "startTime"))
          .isInstanceOf(WebApplicationException.class)
          .satisfies(
              ex ->
                  assertThat(((WebApplicationException) ex).getResponse().getStatus())
                      .isEqualTo(400));
    }

    @Test
    @DisplayName("should_throw_400_when_value_null")
    void shouldRejectNull() {
      assertThatThrownBy(() -> WebVitalsTimeParser.parseQueryInstant(null, "endTime"))
          .isInstanceOf(WebApplicationException.class)
          .satisfies(
              ex ->
                  assertThat(((WebApplicationException) ex).getResponse().getStatus())
                      .isEqualTo(400));
    }

    @Test
    @DisplayName("should_throw_400_when_ISO_invalid")
    void shouldRejectInvalidIso() {
      assertThatThrownBy(() -> WebVitalsTimeParser.parseQueryInstant("not-a-date", "startTime"))
          .isInstanceOf(WebApplicationException.class)
          .satisfies(
              ex ->
                  assertThat(((WebApplicationException) ex).getResponse().getStatus())
                      .isEqualTo(400));
    }
  }
}
