package org.dreamhorizon.pulseserver.util;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QueryTimestampEnricher {

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:m:s");
  private static final DateTimeFormatter TIME_FORMATTER_PADDED = DateTimeFormatter.ofPattern("HH:mm:ss");
  
  private static final Pattern VALID_TIMESTAMP_PATTERN = Pattern.compile("^[0-9\\-\\s:]+$");
  private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");

  public static String enrichQueryWithTimestamp(String query, String timestampString) {
    if (query == null) {
      throw new IllegalArgumentException("Query cannot be null");
    }
    
    query = normalizeAndValidateQuery(query);
    
    if (timestampString != null) {
      timestampString = normalizeAndValidateTimestamp(timestampString);
    }
    
    LocalDateTime dateTime = null;
    String upperQuery = query.toUpperCase();
    int whereIndex = upperQuery.indexOf("WHERE");
    
    if (whereIndex != -1) {
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

    upperQuery = query.toUpperCase();
    whereIndex = upperQuery.indexOf("WHERE");

    if (whereIndex == -1) {
      return addWhereClause(query, partitionFilter);
    } else {
      String whereClause = query.substring(whereIndex);
      if (containsPartitionFilters(whereClause)) {
        log.debug("Query already contains partition filters (year/month/day/hour), skipping enrichment");
        return query;
      }
      
      return appendPartitionFilterToWhereClause(query, whereIndex, partitionFilter);
    }
  }

  private static String addWhereClause(String query, String filter) {
    String upperQuery = query.toUpperCase();
    int groupByIndex = upperQuery.indexOf("GROUP BY");
    int orderByIndex = upperQuery.indexOf("ORDER BY");
    int limitIndex = upperQuery.indexOf("LIMIT");

    int insertPosition = query.length();
    if (groupByIndex != -1) {
      insertPosition = Math.min(insertPosition, groupByIndex);
    }
    if (orderByIndex != -1) {
      insertPosition = Math.min(insertPosition, orderByIndex);
    }
    if (limitIndex != -1) {
      insertPosition = Math.min(insertPosition, limitIndex);
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
    
    if (after.toUpperCase().startsWith("AND")) {
      after = after.substring(3).trim();
      return before + partitionFilter + " AND " + after;
    } else {
      return before + partitionFilter + " AND " + after;
    }
  }

  private static boolean containsPartitionFilters(String whereClause) {
    String upper = whereClause.toUpperCase();
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

  private static String normalizeAndValidateQuery(String query) {
    if (query == null) {
      throw new IllegalArgumentException("Query cannot be null");
    }
    
    String normalized = Normalizer.normalize(query, Normalizer.Form.NFKC);
    
    if (CONTROL_CHAR_PATTERN.matcher(normalized).find()) {
      log.warn("Query contains control characters, removing them");
      normalized = normalized.replaceAll("[\\x00-\\x1F\\x7F]", "");
    }
    
    try {
      byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
      String decoded = new String(bytes, StandardCharsets.UTF_8);
      
      if (!decoded.equals(normalized)) {
        log.warn("Query contains invalid UTF-8 sequences, attempting to recover");
        normalized = decoded;
      }
    } catch (Exception e) {
      log.error("Failed to validate UTF-8 encoding for query", e);
      throw new IllegalArgumentException("Query contains invalid UTF-8 encoding", e);
    }
    
    return normalized;
  }

  private static String normalizeAndValidateTimestamp(String timestampString) {
    if (timestampString == null) {
      return null;
    }
    
    String trimmed = timestampString.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    
    String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
    
    if (normalized == null || normalized.isEmpty()) {
      return null;
    }
    
    if (CONTROL_CHAR_PATTERN.matcher(normalized).find()) {
      log.warn("Timestamp contains control characters: {}", timestampString);
      throw new IllegalArgumentException("Timestamp contains invalid control characters");
    }
    
    if (!VALID_TIMESTAMP_PATTERN.matcher(normalized).matches()) {
      log.warn("Timestamp contains invalid characters: {}", timestampString);
      throw new IllegalArgumentException("Timestamp contains invalid characters. Only digits, hyphens, spaces, and colons are allowed.");
    }
    
    try {
      byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
      String decoded = new String(bytes, StandardCharsets.UTF_8);
      
      if (!decoded.equals(normalized)) {
        log.warn("Timestamp contains invalid UTF-8 sequences");
        throw new IllegalArgumentException("Timestamp contains invalid UTF-8 sequences");
      }
    } catch (Exception e) {
      log.error("Failed to validate UTF-8 encoding for timestamp", e);
      throw new IllegalArgumentException("Timestamp contains invalid UTF-8 encoding", e);
    }
    
    return normalized;
  }
}


