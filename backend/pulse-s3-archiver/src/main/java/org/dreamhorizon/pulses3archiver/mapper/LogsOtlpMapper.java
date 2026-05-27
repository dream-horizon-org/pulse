package org.dreamhorizon.pulses3archiver.mapper;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

public final class LogsOtlpMapper {

  private LogsOtlpMapper() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static List<GenericRecord> map(ExportLogsServiceRequest req, Schema schema) {
    if (req == null) {
      return Collections.emptyList();
    }
    List<GenericRecord> records = new ArrayList<>();
    for (ResourceLogs rl : req.getResourceLogsList()) {
      Map<String, String> resAttrs = AttributeUtils.toMap(rl.getResource().getAttributesList());
      String resSchemaUrl = rl.getSchemaUrl();
      for (ScopeLogs sl : rl.getScopeLogsList()) {
        String scopeName = sl.getScope().getName();
        String scopeVersion = sl.getScope().getVersion();
        String scopeSchemaUrl = sl.getSchemaUrl();
        Map<String, String> scopeAttrs = AttributeUtils.toMap(sl.getScope().getAttributesList());
        for (LogRecord lr : sl.getLogRecordsList()) {
          Map<String, String> logAttrs = AttributeUtils.toMap(lr.getAttributesList());
          records.add(buildRecord(schema, resAttrs, resSchemaUrl,
              scopeName, scopeVersion, scopeSchemaUrl, scopeAttrs, logAttrs, lr));
        }
      }
    }
    return records;
  }

  private static GenericRecord buildRecord(
      Schema schema,
      Map<String, String> ra,
      String resSchemaUrl,
      String scopeName,
      String scopeVersion,
      String scopeSchemaUrl,
      Map<String, String> scopeAttrs,
      Map<String, String> la,
      LogRecord lr) {

    // time_unix_nano may be 0 when the source doesn't know the timestamp;
    // fall back to observed_time_unix_nano (always set by the collector).
    long tsNanos = lr.getTimeUnixNano() != 0L ? lr.getTimeUnixNano() : lr.getObservedTimeUnixNano();
    GenericRecord rec = new GenericData.Record(schema);

    rec.put("Timestamp",          AttributeUtils.nanosToMicros(tsNanos));
    rec.put("TraceId",            AttributeUtils.bytesToHex(lr.getTraceId()));
    rec.put("SpanId",             AttributeUtils.bytesToHex(lr.getSpanId()));
    rec.put("TraceFlags",         (int) lr.getFlags());
    rec.put("SeverityText",       lr.getSeverityText());
    rec.put("SeverityNumber",     lr.getSeverityNumberValue());
    rec.put("ServiceName",        AttributeUtils.get(ra, "service.name"));
    rec.put("Body",               lr.getBody().hasStringValue() ? lr.getBody().getStringValue() : "");
    rec.put("ResourceSchemaUrl",  resSchemaUrl);
    rec.put("ResourceAttributes", ra);
    rec.put("ScopeSchemaUrl",     scopeSchemaUrl);
    rec.put("ScopeName",          scopeName);
    rec.put("ScopeVersion",       scopeVersion);
    rec.put("ScopeAttributes",    scopeAttrs);
    rec.put("LogAttributes",      la);

    // Materialized columns — verbatim CH DDL semantics
    rec.put("SessionId",          AttributeUtils.get(la, "session.id"));
    rec.put("MeteringSessionId",  AttributeUtils.get(la, "pulse.metering.session.id"));
    rec.put("ProjectId",          AttributeUtils.get(ra, "project.id"));
    rec.put("AppVersion",         AttributeUtils.get(ra, "app.build_name"));
    rec.put("SDKVersion",         AttributeUtils.get(ra, "rum.sdk.version"));
    rec.put("Platform",           AttributeUtils.get(ra, "os.name"));
    rec.put("OsVersion",          AttributeUtils.get(ra, "os.version"));
    rec.put("GeoState",           AttributeUtils.get(la, "geo.region.iso_code"));
    rec.put("GeoCountry",         AttributeUtils.get(la, "geo.country.iso_code"));
    rec.put("DeviceModel",        AttributeUtils.get(ra, "device.model.name"));
    rec.put("NetworkProvider",    AttributeUtils.get(la, "network.carrier.name"));
    rec.put("UserId",             AttributeUtils.get(la, "user.id"));
    rec.put("AppInstallationId",  AttributeUtils.get(la, "app.installation.id"));

    String pulseType = la.getOrDefault("pulse.type", "otel");
    if (pulseType.isEmpty()) {
      pulseType = "otel";
    }
    rec.put("PulseType",          pulseType);
    rec.put("EventName",          "custom_event".equals(pulseType)
        ? (lr.getBody().hasStringValue() ? lr.getBody().getStringValue() : "") : "");
    rec.put("ScreenName",         AttributeUtils.get(la, "screen.name"));
    rec.put("ClickType",          AttributeUtils.get(la, "click.type"));
    rec.put("Rage",               AttributeUtils.parseBool(AttributeUtils.get(la, "click.is_rage")));
    rec.put("RageCount",          AttributeUtils.parseUInt8OrZero(AttributeUtils.get(la, "click.rage_count")));
    rec.put("XPer",               AttributeUtils.parseFloat32OrZero(AttributeUtils.get(la, "app.screen.coordinate.x")));
    rec.put("YPer",               AttributeUtils.parseFloat32OrZero(AttributeUtils.get(la, "app.screen.coordinate.y")));
    rec.put("NormXPer",           AttributeUtils.parseFloat32OrZero(AttributeUtils.get(la, "app.screen.coordinate.nx")));
    rec.put("NormYPer",           AttributeUtils.parseFloat32OrZero(AttributeUtils.get(la, "app.screen.coordinate.ny")));
    rec.put("ViewportWidth",      AttributeUtils.parseUInt16OrZero(AttributeUtils.get(la, "device.screen.width")));
    rec.put("ViewportHeight",     AttributeUtils.parseUInt16OrZero(AttributeUtils.get(la, "device.screen.height")));
    rec.put("AspectRatio",        AttributeUtils.get(la, "device.screen.aspect_ratio"));
    rec.put("WebVitalName",       AttributeUtils.get(la, "web_vital.name"));
    rec.put("WebVitalValue",      AttributeUtils.parseFloat64OrZero(AttributeUtils.get(la, "web_vital.value")));
    rec.put("WebVitalRating",     AttributeUtils.get(la, "web_vital.rating"));
    rec.put("Hour",               AttributeUtils.hourFromNanos(tsNanos));

    return rec;
  }
}
