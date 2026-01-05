package org.dreamhorizon.pulseserver.util;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SqlQueryValidator {

  private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
      "(?i)(union|drop|delete|truncate|alter|create|insert|update|exec|execute|script|javascript|vbscript|onload|onerror)",
      Pattern.CASE_INSENSITIVE
  );

  public static ValidationResult validateQuery(String query) {
    if (query == null || query.trim().isEmpty()) {
      return ValidationResult.invalid("Query string cannot be empty");
    }

    String trimmedQuery = query.trim();
    String upperQuery = trimmedQuery.toUpperCase();

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
    String upperQuery = query.toUpperCase();
    
    int whereIndex = upperQuery.indexOf("WHERE");
    if (whereIndex == -1) {
      return false;
    }

    String whereClause = query.substring(whereIndex + 5);
    
    boolean hasYear = containsColumn(whereClause, "year");
    boolean hasMonth = containsColumn(whereClause, "month");
    boolean hasDay = containsColumn(whereClause, "day");
    boolean hasHour = containsColumn(whereClause, "hour");

    if (hasYear && hasMonth && hasDay && hasHour) {
      return true;
    }

    Pattern timestampPattern = Pattern.compile(
        "TIMESTAMP\\s+['\"](\\d{4}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{2}:\\d{2})['\"]",
        Pattern.CASE_INSENSITIVE
    );
    
    return timestampPattern.matcher(whereClause).find();
  }

  private static boolean containsColumn(String whereClause, String columnName) {
    Pattern pattern = Pattern.compile(
        "(?i)\\b" + Pattern.quote(columnName) + "\\b",
        Pattern.CASE_INSENSITIVE
    );
    return pattern.matcher(whereClause).find();
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


