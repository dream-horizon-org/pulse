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
  private static final int MAX_TIMESTAMP_LENGTH = 50;
  private static final Pattern WHERE_PATTERN = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern GROUP_BY_PATTERN = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern ORDER_BY_PATTERN = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  public static String enrichQueryWithTimestamp(String query, String timestampString) {
    if (query == null) {
      throw new IllegalArgumentException("Query cannot be null");
    }

    query = normalizeAndValidateQuery(query);

    if (timestampString != null) {
      timestampString = normalizeAndValidateTimestamp(timestampString);
    }

    LocalDateTime dateTime = null;
    Matcher whereMatcher = WHERE_PATTERN.matcher(query);
    if (whereMatcher.find()) {
      int whereIndex = whereMatcher.start();
      String whereClause = query.substring(whereIndex);
      dateTime = extractTimestampFromWhereClause(whereClause);
    }

    if (dateTime == null && timestampString != null && !timestampString.trim().isEmpty()) {
      dateTime = parseTimestamp(timestampString.trim());
    }
    if (dateTime == null) {
      log.debug("No timestamp found in query or provided timestamp string, skipping enrichment");
      return query;
    }

    int year = dateTime.getYear();
    int month = dateTime.getMonthValue();
    int day = dateTime.getDayOfMonth();
    int hour = dateTime.getHour();

    log.debug("Parsed timestamp {} to year={}, month={}, day={}, hour={}", timestampString, year, month, day, hour);

    String partitionFilter = String.format(
        "year = %d AND month = %d AND day = %d AND hour = %d",
        year, month, day, hour
    );

    whereMatcher = WHERE_PATTERN.matcher(query);
    if (!whereMatcher.find()) {
      return addWhereClause(query, partitionFilter);
    } else {
      int whereIndex = whereMatcher.start();
      String whereClause = query.substring(whereIndex);
      if (containsPartitionFilters(whereClause)) {
        log.debug("Query already contains partition filters (year/month/day/hour), skipping enrichment");
        return query;
      }

      return appendPartitionFilterToWhereClause(query, whereIndex, partitionFilter);
    }
  }

  private static String addWhereClause(String query, String filter) {
    int insertPosition = query.length();

    Matcher groupByMatcher = GROUP_BY_PATTERN.matcher(query);
    if (groupByMatcher.find()) {
      insertPosition = Math.min(insertPosition, groupByMatcher.start());
    }

    Matcher orderByMatcher = ORDER_BY_PATTERN.matcher(query);
    if (orderByMatcher.find()) {
      insertPosition = Math.min(insertPosition, orderByMatcher.start());
    }

    Matcher limitMatcher = LIMIT_PATTERN.matcher(query);
    if (limitMatcher.find()) {
      insertPosition = Math.min(insertPosition, limitMatcher.start());
    }

    String before = query.substring(0, insertPosition).trim();
    String after = query.substring(insertPosition).trim();

    return before + " WHERE " + filter + (after.isEmpty() ? "" : " " + after);
  }

  private static String appendPartitionFilterToWhereClause(String query, int whereIndex, String partitionFilter) {
    int whereEnd = whereIndex + 5;
    while (whereEnd < query.length() && Character.isWhitespace(query.charAt(whereEnd))) {
      whereEnd++;
    }

    String before = query.substring(0, whereEnd);
    String after = query.substring(whereEnd).trim();

    if (after.toUpperCase(Locale.ROOT).startsWith("AND")) {
      after = after.substring(3).trim();
      return before + partitionFilter + " AND " + after;
    } else {
      return before + partitionFilter + " AND " + after;
    }
  }

  private static boolean containsPartitionFilters(String whereClause) {
    String upper = whereClause.toUpperCase(Locale.ROOT);
    return upper.contains("YEAR =") && upper.contains("MONTH =")
        && upper.contains("DAY =") && upper.contains("HOUR =");
  }

  private static LocalDateTime extractTimestampFromWhereClause(String whereClause) {
    java.util.regex.Pattern timestampPattern = java.util.regex.Pattern.compile(
        "TIMESTAMP\\s+['\"](\\d{4}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{2}:\\d{2})['\"]",
        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE
    );

    java.util.regex.Matcher matcher = timestampPattern.matcher(whereClause);

    while (matcher.find()) {
      String timestampStr = matcher.group(1);
      try {
        LocalDateTime dateTime = parseTimestamp(timestampStr);
        if (dateTime != null) {
          log.debug("Extracted timestamp from WHERE clause: {}", timestampStr);
          return dateTime;
        }
      } catch (Exception e) {
        log.debug("Failed to parse extracted timestamp: {}", timestampStr, e);
      }
    }

    return null;
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

  private static final int MAX_QUERY_LENGTH = 100000;

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

    try {
      byte[] bytes = validated.getBytes(StandardCharsets.UTF_8);
      String decoded = new String(bytes, StandardCharsets.UTF_8);

      if (!decoded.equals(validated)) {
        log.warn("Timestamp contains invalid UTF-8 sequences");
        throw new IllegalArgumentException("Timestamp contains invalid UTF-8 sequences");
      }

      byte[] reencoded = validated.getBytes(StandardCharsets.UTF_8);
      if (reencoded.length != bytes.length) {
        log.warn("Timestamp encoding validation failed");
        throw new IllegalArgumentException("Timestamp contains invalid UTF-8 encoding");
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to validate UTF-8 encoding for timestamp", e);
      throw new IllegalArgumentException("Timestamp contains invalid UTF-8 encoding", e);
    }

    if (validated.length() > MAX_TIMESTAMP_LENGTH) {
      log.warn("Validated timestamp exceeds maximum length, rejecting");
      throw new IllegalArgumentException("Validated timestamp exceeds maximum length");
    }

    return validated;
  }
}


