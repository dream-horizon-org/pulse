package org.dreamhorizon.pulseserver.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {
  public LocalDateTime getLocalDateTime(ZoneOffset zoneOffset) {
    return LocalDateTime.now(zoneOffset);
  }

  public LocalDateTime getLocalDateTime() {
    return LocalDateTime.now();
  }

  public static LocalDateTime utcToIstTime(LocalDateTime utcTime) {
    return utcTime.plusHours(5).plusMinutes(30);
  }

  public static String istToUtcTime(String istDateTime) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    LocalDateTime localDateTime = LocalDateTime.parse(istDateTime, formatter);
    // Create ZonedDateTime in IST
    ZonedDateTime istZonedDateTime = localDateTime.atZone(ZoneId.of("Asia/Kolkata"));
    // Convert to UTC
    ZonedDateTime utcZonedDateTime = istZonedDateTime.withZoneSameInstant(ZoneId.of("UTC"));
    // Format and print the UTC time
    return utcZonedDateTime.format(formatter);
  }

}
