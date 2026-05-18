package org.dreamhorizon.pulseserver.errorgrouping.archive;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;

public final class StackTraceEventsMapper {

  private static final DateTimeFormatter CH_TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS").withZone(ZoneOffset.UTC);

  private StackTraceEventsMapper() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static GenericRecord toRecord(StackTraceEvent event, Schema schema) {
    Map<String, String> ra = nullToEmpty(event.getResourceAttributes());
    Map<String, String> la = nullToEmpty(event.getLogAttributes());
    Map<String, String> sa = nullToEmpty(event.getScopeAttributes());

    GenericRecord rec = new GenericData.Record(schema);
    rec.put("Timestamp", parseTimestampMicros(event.getTimestamp()));
    rec.put("EventName", nullToEmpty(event.getEventName()));
    rec.put("Title", nullToEmpty(event.getTitle()));
    rec.put("ExceptionStackTrace", nullToEmpty(event.getExceptionStackTrace()));
    rec.put("ExceptionStackTraceRaw", nullToEmpty(event.getExceptionStackTraceRaw()));
    rec.put("ExceptionMessage", nullToEmpty(event.getExceptionMessage()));
    rec.put("ExceptionType", nullToEmpty(event.getExceptionType()));
    rec.put("Interactions", event.getInteractions() == null ? List.of() : event.getInteractions());
    rec.put("ScreenName", nullToEmpty(event.getScreenName()));
    rec.put("UserId", nullToEmpty(event.getUserId()));
    rec.put("SessionId", nullToEmpty(event.getSessionId()));
    rec.put("Platform", nullToEmpty(event.getPlatform()));
    rec.put("OsVersion", nullToEmpty(event.getOsVersion()));
    rec.put("DeviceModel", nullToEmpty(event.getDeviceModel()));
    rec.put("AppVersionCode", nullToEmpty(event.getAppVersionCode()));
    rec.put("AppVersion", nullToEmpty(event.getAppVersion()));
    rec.put("SdkVersion", nullToEmpty(event.getSdkVersion()));
    rec.put("BundleId", nullToEmpty(event.getBundleId()));
    rec.put("TraceId", nullToEmpty(event.getTraceId()));
    rec.put("SpanId", nullToEmpty(event.getSpanId()));
    rec.put("GroupId", nullToEmpty(event.getGroupId()));
    rec.put("Signature", nullToEmpty(event.getSignature()));
    rec.put("Fingerprint", nullToEmpty(event.getFingerprint()));
    rec.put("ScopeAttributes", sa);
    rec.put("LogAttributes", la);
    rec.put("ResourceAttributes", ra);

    String projectId = ra.getOrDefault("project.id", "");
    rec.put("ProjectId", projectId);
    String pulseType = event.getPulseType();
    if (pulseType == null || pulseType.isBlank()) {
      pulseType = la.getOrDefault("pulse.type", "otel");
    }
    rec.put("PulseType", pulseType);
    rec.put("MeteringSessionId", la.getOrDefault("pulse.metering.session.id", ""));
    rec.put("AppInstallationId", la.getOrDefault("app.installation.id", ""));
    return rec;
  }

  static long parseTimestampMicros(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return Instant.now().toEpochMilli() * 1000L;
    }
    try {
      Instant instant = Instant.from(CH_TS.parse(timestamp.trim()));
      return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1000L;
    } catch (DateTimeParseException e) {
      return Instant.now().toEpochMilli() * 1000L;
    }
  }

  private static Map<String, String> nullToEmpty(Map<String, String> map) {
    if (map == null || map.isEmpty()) {
      return Collections.emptyMap();
    }
    return new HashMap<>(map);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
