package org.dreamhorizon.pulses3archiver.mapper;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.Schema;

public final class TracesOtlpMapper {

  private TracesOtlpMapper() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static List<GenericRecord> map(ExportTraceServiceRequest req, Schema schema) {
    if (req == null) {
      return Collections.emptyList();
    }
    List<GenericRecord> records = new ArrayList<>();
    for (ResourceSpans rs : req.getResourceSpansList()) {
      Map<String, String> resAttrs = AttributeUtils.toMap(
          rs.getResource().getAttributesList());
      for (ScopeSpans ss : rs.getScopeSpansList()) {
        String scopeName = ss.getScope().getName();
        String scopeVersion = ss.getScope().getVersion();
        for (Span sp : ss.getSpansList()) {
          Map<String, String> spanAttrs = AttributeUtils.toMap(sp.getAttributesList());
          records.add(buildRecord(schema, resAttrs, scopeName, scopeVersion, spanAttrs, sp));
        }
      }
    }
    return records;
  }

  private static GenericRecord buildRecord(
      Schema schema,
      Map<String, String> ra,
      String scopeName,
      String scopeVersion,
      Map<String, String> sa,
      Span sp) {

    long tsNanos = sp.getStartTimeUnixNano();
    GenericRecord rec = new GenericData.Record(schema);

    rec.put("Timestamp",         AttributeUtils.nanosToMicros(tsNanos));
    rec.put("TraceId",           AttributeUtils.bytesToHex(sp.getTraceId()));
    rec.put("SpanId",            AttributeUtils.bytesToHex(sp.getSpanId()));
    rec.put("ParentSpanId",      AttributeUtils.bytesToHex(sp.getParentSpanId()));
    rec.put("TraceState",        sp.getTraceState().isEmpty() ? "" : sp.getTraceState().toString());
    rec.put("SpanName",          sp.getName());
    rec.put("SpanKind",          sp.getKind().name());
    rec.put("ServiceName",       AttributeUtils.get(ra, "service.name"));
    rec.put("ResourceAttributes", ra);
    rec.put("ScopeName",         scopeName);
    rec.put("ScopeVersion",      scopeVersion);
    rec.put("SpanAttributes",    sa);
    rec.put("Duration",          sp.getEndTimeUnixNano() - tsNanos);
    rec.put("StatusCode",        sp.getStatus().getCode().name());
    rec.put("StatusMessage",     sp.getStatus().getMessage());

    List<Long> evTs = new ArrayList<>();
    List<String> evName = new ArrayList<>();
    List<Map<String, String>> evAttrs = new ArrayList<>();
    for (Span.Event ev : sp.getEventsList()) {
      evTs.add(AttributeUtils.nanosToMicros(ev.getTimeUnixNano()));
      evName.add(ev.getName());
      evAttrs.add(AttributeUtils.toMap(ev.getAttributesList()));
    }
    rec.put("EventsTimestamp",  evTs);
    rec.put("EventsName",       evName);
    rec.put("EventsAttributes", evAttrs);

    List<String> linkTraceId = new ArrayList<>();
    List<String> linkSpanId = new ArrayList<>();
    List<String> linkTraceState = new ArrayList<>();
    List<Map<String, String>> linkAttrs = new ArrayList<>();
    for (Span.Link lk : sp.getLinksList()) {
      linkTraceId.add(AttributeUtils.bytesToHex(lk.getTraceId()));
      linkSpanId.add(AttributeUtils.bytesToHex(lk.getSpanId()));
      linkTraceState.add(lk.getTraceState().isEmpty() ? "" : lk.getTraceState().toString());
      linkAttrs.add(AttributeUtils.toMap(lk.getAttributesList()));
    }
    rec.put("LinksTraceId",     linkTraceId);
    rec.put("LinksSpanId",      linkSpanId);
    rec.put("LinksTraceState",  linkTraceState);
    rec.put("LinksAttributes",  linkAttrs);

    // Materialized columns — verbatim CH DDL semantics
    rec.put("ProjectId",         AttributeUtils.get(ra, "project.id"));
    rec.put("SpanType",          AttributeUtils.get(sa, "pulse.type"));
    rec.put("PulseType",         AttributeUtils.get(sa, "pulse.type"));
    rec.put("SessionId",         AttributeUtils.get(sa, "session.id"));
    rec.put("AppVersion",        AttributeUtils.get(ra, "app.version"));
    rec.put("SDKVersion",        AttributeUtils.get(ra, "telemetry.sdk.version"));
    rec.put("Platform",          AttributeUtils.get(ra, "os.name"));
    rec.put("OsVersion",         AttributeUtils.get(ra, "os.version"));
    rec.put("GeoState",          AttributeUtils.get(sa, "geo.region.iso_code"));
    rec.put("GeoCountry",        AttributeUtils.get(sa, "geo.country.iso_code"));
    rec.put("DeviceModel",       AttributeUtils.get(ra, "device.model.identifier"));
    rec.put("NetworkProvider",   AttributeUtils.get(sa, "network.carrier.name"));
    rec.put("MeteringSessionId", AttributeUtils.get(sa, "metering.session.id"));
    rec.put("UserId",            AttributeUtils.get(sa, "user.id"));
    rec.put("AppInstallationId", AttributeUtils.get(sa, "app.installation.id"));
    rec.put("HttpUrl",           AttributeUtils.coalesce(sa, "http.url", "url.full"));
    rec.put("HttpHost",          AttributeUtils.coalesce(sa, "net.peer.name", "server.address"));
    rec.put("HttpMethod",        AttributeUtils.coalesce(sa, "http.method", "http.request.method"));
    rec.put("HttpStatusCode",    AttributeUtils.parseUInt16OrZero(
        AttributeUtils.coalesce(sa, "http.status_code", "http.response.status_code")));
    rec.put("GraphqlType",       AttributeUtils.get(sa, "graphql.operation.type"));
    rec.put("GraphqlName",       AttributeUtils.get(sa, "graphql.operation.name"));
    rec.put("ScreenName",        AttributeUtils.get(sa, "screen.name"));
    rec.put("Hour",              AttributeUtils.hourFromNanos(tsNanos));

    return rec;
  }
}
