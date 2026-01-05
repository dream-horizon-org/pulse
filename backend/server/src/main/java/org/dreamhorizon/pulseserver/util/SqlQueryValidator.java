package org.dreamhorizon.pulseserver.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SqlQueryValidator {

  private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
      "(?i)(union|drop|delete|truncate|alter|create|insert|update|exec|execute|script|javascript|vbscript|onload|onerror)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");
  private static final Pattern WHERE_PATTERN = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final int MAX_QUERY_LENGTH = 100000;

  public static ValidationResult validateQuery(String query) {
    if (query == null || query.trim().isEmpty()) {
      return ValidationResult.invalid("Query string cannot be empty");
    }

    try {
      query = normalizeAndValidateQuery(query);
    } catch (IllegalArgumentException e) {
      log.warn("Query validation failed due to encoding issues: {}", e.getMessage());
      return ValidationResult.invalid("Query contains invalid encoding: " + e.getMessage());
    }

    String trimmedQuery = query.trim();
    String upperQuery = trimmedQuery.toUpperCase(Locale.ROOT);

    if (!upperQuery.startsWith("SELECT")) {
      return ValidationResult.invalid("Query must start with SELECT");
    }

    if (!upperQuery.contains("FROM")) {
      return ValidationResult.invalid("Query must contain a FROM clause");
    }

    if (SQL_INJECTION_PATTERN.matcher(trimmedQuery).find()) {
      return ValidationResult.invalid("Query contains potentially dangerous operations");
    }

    if (!hasTimestampInWhereClause(trimmedQuery)) {
      return ValidationResult.invalid("Query must include timestamp filter in WHERE clause (year, month, day, hour) or TIMESTAMP literals");
    }

    return ValidationResult.valid();
  }

  private static boolean hasTimestampInWhereClause(String query) {
    Matcher whereMatcher = WHERE_PATTERN.matcher(query);
    if (!whereMatcher.find()) {
      return false;
    }

    int whereEnd = whereMatcher.end();
    String whereClause = query.substring(whereEnd);

    boolean hasYear = containsColumn(whereClause, "year");
    boolean hasMonth = containsColumn(whereClause, "month");
    boolean hasDay = containsColumn(whereClause, "day");
    boolean hasHour = containsColumn(whereClause, "hour");

    if (hasYear && hasMonth && hasDay && hasHour) {
      return true;
    }

    Pattern timestampPattern = Pattern.compile(
        "TIMESTAMP\\s+['\"](\\d{4}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{2}:\\d{2})['\"]",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    return timestampPattern.matcher(whereClause).find();
  }

  private static boolean containsColumn(String whereClause, String columnName) {
    Pattern pattern = Pattern.compile(
        "(?i)\\b" + Pattern.quote(columnName) + "\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    return pattern.matcher(whereClause).find();
  }

  private static String normalizeAndValidateQuery(String query) {
    if (query == null) {
      throw new IllegalArgumentException("Query cannot be null");
    }

    if (query.length() > MAX_QUERY_LENGTH) {
      throw new IllegalArgumentException("Query exceeds maximum length");
    }

    String validated = query;

    if (CONTROL_CHAR_PATTERN.matcher(validated).find()) {
      log.warn("Query contains control characters, removing them");
      validated = validated.replaceAll("[\\x00-\\x1F\\x7F]", "");
    }

    try {
      byte[] bytes = validated.getBytes(StandardCharsets.UTF_8);
      String decoded = new String(bytes, StandardCharsets.UTF_8);

      if (!decoded.equals(validated)) {
        log.warn("Query contains invalid UTF-8 sequences, attempting to recover");
        validated = decoded;
      }

      byte[] reencoded = validated.getBytes(StandardCharsets.UTF_8);
      if (reencoded.length != bytes.length) {
        log.warn("Query encoding validation failed");
        throw new IllegalArgumentException("Query contains invalid UTF-8 encoding");
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to validate UTF-8 encoding for query", e);
      throw new IllegalArgumentException("Query contains invalid UTF-8 encoding", e);
    }

    if (validated.length() > MAX_QUERY_LENGTH) {
      log.warn("Validated query exceeds maximum length, rejecting");
      throw new IllegalArgumentException("Validated query exceeds maximum length");
    }

    return validated;
  }

  public static class ValidationResult {
    private final boolean valid;
    private final String errorMessage;

    private ValidationResult(boolean valid, String errorMessage) {
      this.valid = valid;
      this.errorMessage = errorMessage;
    }

    public static ValidationResult valid() {
      return new ValidationResult(true, null);
    }

    public static ValidationResult invalid(String errorMessage) {
      return new ValidationResult(false, errorMessage);
    }

    public boolean isValid() {
      return valid;
    }

    public String getErrorMessage() {
      return errorMessage;
    }
  }
}


