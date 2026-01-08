package org.dreamhorizon.pulseserver.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QueryTimestampEnricher {

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:m:s");
  private static final DateTimeFormatter TIME_FORMATTER_PADDED = DateTimeFormatter.ofPattern("HH:mm:ss");

  private static final Pattern VALID_TIMESTAMP_PATTERN = Pattern.compile("^[0-9\\-\\s:]+$");
  private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");
  private static final Pattern WHERE_PATTERN = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern GROUP_BY_PATTERN = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern ORDER_BY_PATTERN = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
      "TIMESTAMP\\s+['\"](\\d{4}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{2}:\\d{2})['\"]",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final int MAX_TIMESTAMP_LENGTH = 50;
  private static final int MAX_QUERY_LENGTH = 100000;
  private static final int TIMESTAMP_SEARCH_BACKWARD_CHARS = 100;
  private static final int END_TIMESTAMP_SEARCH_BACKWARD_CHARS = 50;

  public static String enrichQueryWithTimestamp(String query, String timestampString) {
    if (query == null) {
      throw new IllegalArgumentException("Query cannot be null");
    }

    query = normalizeAndValidateQuery(query);

    if (timestampString != null) {
      timestampString = normalizeAndValidateTimestamp(timestampString);
    }

    LocalDateTime startDateTime = extractStartTimestamp(query);
    if (startDateTime == null && timestampString != null && !timestampString.trim().isEmpty()) {
      startDateTime = parseTimestamp(timestampString.trim());
    }

    if (startDateTime == null) {
      log.warn("No timestamp found in query or provided timestamp string, skipping enrichment. Query: {}", query);
      return query;
    }

    log.info("Successfully extracted timestamp: {} from query, will add partition filters", startDateTime);

    LocalDateTime endDateTime = extractEndTimestamp(query);
    String partitionFilter = buildPartitionFilter(startDateTime, endDateTime, query);

    Matcher whereMatcher = WHERE_PATTERN.matcher(query);
    if (!whereMatcher.find()) {
      return addWhereClause(query, partitionFilter);
    }

    int whereIndex = whereMatcher.start();
    String whereClause = query.substring(whereIndex);
    if (containsPartitionFilters(whereClause)) {
      log.debug("Query already contains partition filters (year/month/day/hour), skipping enrichment");
      return query;
    }

    return appendPartitionFilterToWhereClause(query, whereIndex, partitionFilter);
  }

  private static LocalDateTime extractStartTimestamp(String query) {
    Matcher whereMatcher = WHERE_PATTERN.matcher(query);
    if (!whereMatcher.find()) {
      return null;
    }

    String whereClause = query.substring(whereMatcher.start());
    return extractTimestampFromWhereClause(whereClause);
  }

  private static LocalDateTime extractEndTimestamp(String query) {
    Matcher whereMatcher = WHERE_PATTERN.matcher(query);
    if (!whereMatcher.find()) {
      return null;
    }

    String whereClause = query.substring(whereMatcher.start());
    return extractEndTimestampFromWhereClause(whereClause);
  }

  private static String buildPartitionFilter(LocalDateTime startDateTime, LocalDateTime endDateTime, String query) {
    int startYear = startDateTime.getYear();
    int startMonth = startDateTime.getMonthValue();
    int startDay = startDateTime.getDayOfMonth();
    int startHour = startDateTime.getHour();

    log.debug("Parsed start timestamp {} to year={}, month={}, day={}, hour={}",
        startDateTime, startYear, startMonth, startDay, startHour);

    if (endDateTime == null) {
      return buildPartitionFilterForStartOnly(startYear, startMonth, startDay, startHour, query);
    }

    int endYear = endDateTime.getYear();
    int endMonth = endDateTime.getMonthValue();
    int endDay = endDateTime.getDayOfMonth();
    int endHour = endDateTime.getHour();

    log.debug("Parsed end timestamp {} to year={}, month={}, day={}, hour={}",
        endDateTime, endYear, endMonth, endDay, endHour);

    if (startYear == endYear && startMonth == endMonth && startDay == endDay && startHour == endHour) {
      log.debug("Start and end in same partition, using equality filter for better performance");
      return String.format("year = %d AND month = %d AND day = %d AND hour = %d",
          startYear, startMonth, startDay, startHour);
    }

    return buildPartitionFilterForRange(startYear, startMonth, startDay, startHour,
        endYear, endMonth, endDay, endHour);
  }

  private static String buildPartitionFilterForStartOnly(int startYear, int startMonth, int startDay,
      int startHour, String query) {
    Matcher whereMatcher = WHERE_PATTERN.matcher(query);
    if (whereMatcher.find()) {
      String whereClause = query.substring(whereMatcher.start());
      String upperClause = whereClause.toUpperCase(Locale.ROOT);
      boolean hasGreaterThanOrEqual = upperClause.contains("TIMESTAMP") && upperClause.contains(">=");

      if (hasGreaterThanOrEqual) {
        log.debug("Using >= partition filter for start-only timestamp");
        return String.format("(year, month, day, hour) >= (%d, %d, %d, %d)",
            startYear, startMonth, startDay, startHour);
      }
    }

    return String.format("year = %d AND month = %d AND day = %d AND hour = %d",
        startYear, startMonth, startDay, startHour);
  }

  private static String buildPartitionFilterForRange(int startYear, int startMonth, int startDay, int startHour,
      int endYear, int endMonth, int endDay, int endHour) {
    return String.format(
        "(year, month, day, hour) >= (%d, %d, %d, %d) AND (year, month, day, hour) <= (%d, %d, %d, %d)",
        startYear, startMonth, startDay, startHour,
        endYear, endMonth, endDay, endHour
    );
  }

  private static String addWhereClause(String query, String filter) {
    int insertPosition = query.length();

    insertPosition = findEarliestPosition(query, insertPosition, GROUP_BY_PATTERN);
    insertPosition = findEarliestPosition(query, insertPosition, ORDER_BY_PATTERN);
    insertPosition = findEarliestPosition(query, insertPosition, LIMIT_PATTERN);

    String before = query.substring(0, insertPosition).trim();
    String after = query.substring(insertPosition).trim();

    return before + " WHERE " + filter + (after.isEmpty() ? "" : " " + after);
  }

  private static int findEarliestPosition(String query, int currentPosition, Pattern pattern) {
    Matcher matcher = pattern.matcher(query);
    if (matcher.find()) {
      return Math.min(currentPosition, matcher.start());
    }
    return currentPosition;
  }

  private static String appendPartitionFilterToWhereClause(String query, int whereIndex, String partitionFilter) {
    int whereEnd = whereIndex + 5;
    while (whereEnd < query.length() && Character.isWhitespace(query.charAt(whereEnd))) {
      whereEnd++;
    }

    String before = query.substring(0, whereEnd);
    String after = query.substring(whereEnd).trim();
    String upperAfter = after.toUpperCase(Locale.ROOT);

    if (upperAfter.startsWith("AND")) {
      after = after.substring(3).trim();
    }

    return before + partitionFilter + " AND " + after;
  }

  private static boolean containsPartitionFilters(String whereClause) {
    String upper = whereClause.toUpperCase(Locale.ROOT);
    return upper.contains("YEAR =") && upper.contains("MONTH =")
        && upper.contains("DAY =") && upper.contains("HOUR =");
  }

  private static LocalDateTime extractTimestampFromWhereClause(String whereClause) {
    log.debug("Extracting timestamp from WHERE clause: {}", whereClause);
    Matcher matcher = TIMESTAMP_PATTERN.matcher(whereClause);

    LocalDateTime startDateTime = null;
    LocalDateTime endDateTime = null;

    if (!matcher.find()) {
      log.debug("No TIMESTAMP literals found in WHERE clause");
      return null;
    }

    matcher.reset();
    while (matcher.find()) {
      String timestampStr = matcher.group(1);
      log.debug("Found TIMESTAMP literal: {}", timestampStr);
      try {
        LocalDateTime dateTime = parseTimestamp(timestampStr);
        if (dateTime != null) {
          int matchStart = matcher.start();
          int searchStart = Math.max(0, matchStart - TIMESTAMP_SEARCH_BACKWARD_CHARS);
          String beforeMatch = whereClause.substring(searchStart, matchStart).toUpperCase(Locale.ROOT);

          log.debug("Context before TIMESTAMP (last {} chars): {}", TIMESTAMP_SEARCH_BACKWARD_CHARS, beforeMatch);

          if (isStartTimeOperator(beforeMatch)) {
            if (startDateTime == null || dateTime.isBefore(startDateTime)) {
              startDateTime = dateTime;
              log.info("Extracted start timestamp from WHERE clause: {}", timestampStr);
            }
          } else if (isEndTimeOperator(beforeMatch)) {
            if (endDateTime == null || dateTime.isAfter(endDateTime)) {
              endDateTime = dateTime;
              log.info("Extracted end timestamp from WHERE clause: {}", timestampStr);
            }
          } else if (startDateTime == null) {
            startDateTime = dateTime;
            log.info("Extracted timestamp from WHERE clause (no operator found): {}", timestampStr);
          }
        }
      } catch (Exception e) {
        log.warn("Failed to parse extracted timestamp: {}", timestampStr, e);
      }
    }

    if (startDateTime != null) {
      return startDateTime;
    }

    if (endDateTime != null) {
      log.debug("Using end timestamp for partition filtering: {}", endDateTime);
      return endDateTime;
    }

    return null;
  }

  private static boolean isStartTimeOperator(String beforeMatch) {
    return beforeMatch.contains(">=") || (beforeMatch.contains(">") && !beforeMatch.contains("<>"));
  }

  private static boolean isEndTimeOperator(String beforeMatch) {
    return beforeMatch.contains("<=") || (beforeMatch.contains("<") && !beforeMatch.contains("<>"));
  }

  private static LocalDateTime extractEndTimestampFromWhereClause(String whereClause) {
    Matcher matcher = TIMESTAMP_PATTERN.matcher(whereClause);
    LocalDateTime endDateTime = null;

    while (matcher.find()) {
      String timestampStr = matcher.group(1);
      try {
        LocalDateTime dateTime = parseTimestamp(timestampStr);
        if (dateTime != null) {
          int matchStart = matcher.start();
          String beforeMatch = whereClause.substring(
              Math.max(0, matchStart - END_TIMESTAMP_SEARCH_BACKWARD_CHARS), matchStart).toUpperCase(Locale.ROOT);

          if (isEndTimeOperator(beforeMatch)) {
            if (endDateTime == null || dateTime.isAfter(endDateTime)) {
              endDateTime = dateTime;
            }
          }
        }
      } catch (Exception e) {
        log.debug("Failed to parse extracted end timestamp: {}", timestampStr, e);
      }
    }

    return endDateTime;
  }

  private static LocalDateTime parseTimestamp(String timestampString) {
    if (timestampString == null || timestampString.trim().isEmpty()) {
      return null;
    }

    try {
      if (timestampString.contains("-") && timestampString.contains(" ")) {
        return LocalDateTime.parse(timestampString, DATE_TIME_FORMATTER);
      }

      LocalTime time;
      try {
        time = LocalTime.parse(timestampString, TIME_FORMATTER_PADDED);
      } catch (DateTimeParseException e) {
        time = LocalTime.parse(timestampString, TIME_FORMATTER);
      }

      return LocalDateTime.of(LocalDate.now(), time);

    } catch (DateTimeParseException e) {
      log.error("Failed to parse timestamp string: {}", timestampString, e);
      return null;
    }
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

    validated = validateUtf8Encoding(validated, "Query");

    if (validated.length() > MAX_QUERY_LENGTH) {
      log.warn("Validated query exceeds maximum length, rejecting");
      throw new IllegalArgumentException("Validated query exceeds maximum length");
    }

    return validated;
  }

  private static String normalizeAndValidateTimestamp(String timestampString) {
    if (timestampString == null) {
      return null;
    }

    if (timestampString.length() > MAX_TIMESTAMP_LENGTH) {
      throw new IllegalArgumentException("Timestamp exceeds maximum length");
    }

    String trimmed = timestampString.trim();
    if (trimmed.isEmpty()) {
      return null;
    }

    String validated = trimmed;

    if (CONTROL_CHAR_PATTERN.matcher(validated).find()) {
      log.warn("Timestamp contains control characters: {}", timestampString);
      throw new IllegalArgumentException("Timestamp contains invalid control characters");
    }

    if (!VALID_TIMESTAMP_PATTERN.matcher(validated).matches()) {
      log.warn("Timestamp contains invalid characters: {}", timestampString);
      throw new IllegalArgumentException("Timestamp contains invalid characters. Only digits, hyphens, spaces, and colons are allowed.");
    }

    validated = validateUtf8Encoding(validated, "Timestamp");

    if (validated.length() > MAX_TIMESTAMP_LENGTH) {
      log.warn("Validated timestamp exceeds maximum length, rejecting");
      throw new IllegalArgumentException("Validated timestamp exceeds maximum length");
    }

    return validated;
  }

  private static String validateUtf8Encoding(String input, String type) {
    try {
      byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
      String decoded = new String(bytes, StandardCharsets.UTF_8);

      if (!decoded.equals(input)) {
        log.warn("{} contains invalid UTF-8 sequences, attempting to recover", type);
        input = decoded;
      }

      byte[] reencoded = input.getBytes(StandardCharsets.UTF_8);
      if (reencoded.length != bytes.length) {
        log.warn("{} encoding validation failed", type);
        throw new IllegalArgumentException(type + " contains invalid UTF-8 encoding");
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to validate UTF-8 encoding for {}", type, e);
      throw new IllegalArgumentException(type + " contains invalid UTF-8 encoding", e);
    }

    return input;
  }
}
