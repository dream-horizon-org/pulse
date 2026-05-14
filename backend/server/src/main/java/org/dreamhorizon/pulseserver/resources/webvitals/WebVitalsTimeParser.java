package org.dreamhorizon.pulseserver.resources.webvitals;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.dreamhorizon.pulseserver.error.ServiceError;

/**
 * Parses {@code startTime} / {@code endTime} query values for web vitals endpoints.
 * Accepts epoch milliseconds when the value is all ASCII digits (as sent by the
 * dashboard) or ISO-8601 instants.
 */
public final class WebVitalsTimeParser {

  private WebVitalsTimeParser() { }

  /**
   * Parses a required instant from a query parameter string.
   *
   * @param raw unparsed query value
   * @param paramName parameter name for error messages (e.g. {@code startTime})
   * @return parsed instant in UTC
   */
  public static Instant parseQueryInstant(final String raw, final String paramName) {
    if (raw == null || raw.isBlank()) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
          "Query parameter '" + paramName + "' is required.", null);
    }
    String trimmed = raw.trim();
    if (trimmed.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
      try {
        return Instant.ofEpochMilli(Long.parseLong(trimmed));
      } catch (NumberFormatException e) {
        throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
            invalidInstantMessage(paramName),
            e.getMessage());
      }
    }
    try {
      return Instant.parse(trimmed);
    } catch (DateTimeParseException e) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
          invalidInstantMessage(paramName),
          e.getMessage());
    }
  }

  private static String invalidInstantMessage(final String paramName) {
    return "Query parameter '"
        + paramName
        + "' must be epoch milliseconds or an ISO-8601 instant.";
  }
}
