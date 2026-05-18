package org.dreamhorizon.pulses3archiver.mapper;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.ExponentialHistogram;
import io.opentelemetry.proto.metrics.v1.ExponentialHistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Histogram;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.Summary;
import io.opentelemetry.proto.metrics.v1.SummaryDataPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

public final class MetricsOtlpMapper {

  public enum MetricTable {
    SUM, HISTOGRAM, EXP_HISTOGRAM, SUMMARY
  }

  private MetricsOtlpMapper() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static Map<MetricTable, List<GenericRecord>> map(
      ExportMetricsServiceRequest req,
      Map<MetricTable, Schema> schemas) {

    Map<MetricTable, List<GenericRecord>> result = new EnumMap<>(MetricTable.class);
    for (MetricTable table : MetricTable.values()) {
      result.put(table, new ArrayList<>());
    }
    if (req == null) {
      return result;
    }

    for (ResourceMetrics rm : req.getResourceMetricsList()) {
      Map<String, String> ra = AttributeUtils.toMap(rm.getResource().getAttributesList());
      String resSchemaUrl = rm.getSchemaUrl();
      for (ScopeMetrics sm : rm.getScopeMetricsList()) {
        String scopeName = sm.getScope().getName();
        String scopeVersion = sm.getScope().getVersion();
        String scopeSchemaUrl = sm.getSchemaUrl();
        for (Metric metric : sm.getMetricsList()) {
          switch (metric.getDataCase()) {
            case SUM:
              mapSum(metric, ra, resSchemaUrl, scopeName, scopeVersion, scopeSchemaUrl,
                  schemas.get(MetricTable.SUM), result.get(MetricTable.SUM));
              break;
            case HISTOGRAM:
              mapHistogram(metric, ra, resSchemaUrl, scopeName, scopeVersion, scopeSchemaUrl,
                  schemas.get(MetricTable.HISTOGRAM), result.get(MetricTable.HISTOGRAM));
              break;
            case EXPONENTIAL_HISTOGRAM:
              mapExpHistogram(metric, ra, resSchemaUrl, scopeName, scopeVersion, scopeSchemaUrl,
                  schemas.get(MetricTable.EXP_HISTOGRAM), result.get(MetricTable.EXP_HISTOGRAM));
              break;
            case SUMMARY:
              mapSummary(metric, ra, resSchemaUrl, scopeName, scopeVersion, scopeSchemaUrl,
                  schemas.get(MetricTable.SUMMARY), result.get(MetricTable.SUMMARY));
              break;
            default:
              break;
          }
        }
      }
    }
    return result;
  }

  private static void putCommonMetricFields(
      GenericRecord rec,
      Map<String, String> ra,
      String resSchemaUrl,
      String scopeName,
      String scopeVersion,
      String scopeSchemaUrl,
      Map<String, String> attrs,
      String metricName,
      String metricDescription,
      String metricUnit,
      long startTimeNanos,
      long timeNanos,
      long flags) {

    rec.put("ResourceAttributes",    ra);
    rec.put("ResourceSchemaUrl",     resSchemaUrl);
    rec.put("ScopeName",             scopeName);
    rec.put("ScopeVersion",          scopeVersion);
    rec.put("ScopeAttributes",       Collections.emptyMap());
    rec.put("ScopeDroppedAttrCount", 0);
    rec.put("ScopeSchemaUrl",        scopeSchemaUrl);
    rec.put("ServiceName",           AttributeUtils.get(ra, "service.name"));
    rec.put("MetricName",            metricName);
    rec.put("MetricDescription",     metricDescription);
    rec.put("MetricUnit",            metricUnit);
    rec.put("Attributes",            attrs);
    rec.put("StartTimeUnix",         AttributeUtils.nanosToMicros(startTimeNanos));
    rec.put("TimeUnix",              AttributeUtils.nanosToMicros(timeNanos));
    rec.put("Flags",                 (int) flags);

    // Materialized columns — CH DDL semantics (metrics use Attributes, not SpanAttributes)
    rec.put("ProjectId",         AttributeUtils.get(ra, "project.id"));
    rec.put("SessionId",         AttributeUtils.get(attrs, "session.id"));
    rec.put("MeteringSessionId", AttributeUtils.get(attrs, "pulse.metering.session.id"));
    rec.put("AppVersion",        AttributeUtils.get(attrs, "app.build_name"));
    rec.put("SDKVersion",        AttributeUtils.get(ra, "rum.sdk.version"));
    rec.put("Platform",          AttributeUtils.get(ra, "os.name"));
    rec.put("OsVersion",         AttributeUtils.get(ra, "os.version"));
    rec.put("GeoState",          AttributeUtils.get(attrs, "geo.region.iso_code"));
    rec.put("GeoCountry",        AttributeUtils.get(attrs, "geo.country.iso_code"));
    rec.put("DeviceModel",       AttributeUtils.get(ra, "device.model.name"));
    rec.put("NetworkProvider",   AttributeUtils.get(attrs, "network.carrier.name"));
    // UserId: coalesce(nullIf(user.id,''), nullIf(app.installation.id,''))
    rec.put("UserId",            AttributeUtils.coalesce(attrs, "user.id", "app.installation.id"));
    rec.put("Hour",              AttributeUtils.hourFromNanos(timeNanos));
  }

  private static void mapSum(Metric metric, Map<String, String> ra, String resSchemaUrl,
      String scopeName, String scopeVersion, String scopeSchemaUrl,
      Schema schema, List<GenericRecord> out) {

    for (NumberDataPoint dp : metric.getSum().getDataPointsList()) {
      Map<String, String> attrs = AttributeUtils.toMap(dp.getAttributesList());
      GenericRecord rec = new GenericData.Record(schema);
      putCommonMetricFields(rec, ra, resSchemaUrl, scopeName, scopeVersion, scopeSchemaUrl,
          attrs, metric.getName(), metric.getDescription(), metric.getUnit(),
          dp.getStartTimeUnixNano(), dp.getTimeUnixNano(), dp.getFlags());

      rec.put("Value", dp.hasAsDouble() ? dp.getAsDouble() : (double) dp.getAsInt());
      rec.put("AggregationTemporality", metric.getSum().getAggregationTemporalityValue());
      rec.put("IsMonotonic",            metric.getSum().getIsMonotonic());

      List<Long> exTs = new ArrayList<>();
      List<Double> exVal = new ArrayList<>();
      List<String> exSpan = new ArrayList<>();
      List<String> exTrace = new ArrayList<>();
      List<Map<String, String>> exAttrs = new ArrayList<>();
      for (var ex : dp.getExemplarsList()) {
        exTs.add(AttributeUtils.nanosToMicros(ex.getTimeUnixNano()));
        exVal.add(ex.hasAsDouble() ? ex.getAsDouble() : (double) ex.getAsInt());
        exSpan.add(AttributeUtils.bytesToHex(ex.getSpanId()));
        exTrace.add(AttributeUtils.bytesToHex(ex.getTraceId()));
        exAttrs.add(AttributeUtils.toMap(ex.getFilteredAttributesList()));
      }
      rec.put("ExemplarsFilteredAttributes", exAttrs);
      rec.put("ExemplarsTimeUnix",           exTs);
      rec.put("ExemplarsValue",              exVal);
      rec.put("ExemplarsSpanId",             exSpan);
      rec.put("ExemplarsTraceId",            exTrace);
      out.add(rec);
    }
  }

  private static void mapHistogram(Metric metric, Map<String, String> ra, String resSchemaUrl,
      String scopeName, String scopeVersion, String scopeSchemaUrl,
      Schema schema, List<GenericRecord> out) {

    Histogram hist = metric.getHistogram();
    for (HistogramDataPoint dp : hist.getDataPointsList()) {
      Map<String, String> attrs = AttributeUtils.toMap(dp.getAttributesList());
      GenericRecord rec = new GenericData.Record(schema);
      putCommonMetricFields(rec, ra, resSchemaUrl, scopeName, scopeVersion, scopeSchemaUrl,
          attrs, metric.getName(), metric.getDescription(), metric.getUnit(),
          dp.getStartTimeUnixNano(), dp.getTimeUnixNano(), dp.getFlags());

      rec.put("Count",                  dp.getCount());
      rec.put("Sum",                    dp.hasSum() ? dp.getSum() : 0d);
      rec.put("Min",                    dp.hasMin() ? dp.getMin() : 0d);
      rec.put("Max",                    dp.hasMax() ? dp.getMax() : 0d);
      rec.put("BucketCounts",           dp.getBucketCountsList());
      rec.put("ExplicitBounds",         dp.getExplicitBoundsList());
      rec.put("AggregationTemporality", hist.getAggregationTemporalityValue());

      List<Long> exTs = new ArrayList<>();
      List<Double> exVal = new ArrayList<>();
      List<String> exSpan = new ArrayList<>();
      List<String> exTrace = new ArrayList<>();
      List<Map<String, String>> exAttrs = new ArrayList<>();
      for (var ex : dp.getExemplarsList()) {
        exTs.add(AttributeUtils.nanosToMicros(ex.getTimeUnixNano()));
        exVal.add(ex.hasAsDouble() ? ex.getAsDouble() : (double) ex.getAsInt());
        exSpan.add(AttributeUtils.bytesToHex(ex.getSpanId()));
        exTrace.add(AttributeUtils.bytesToHex(ex.getTraceId()));
        exAttrs.add(AttributeUtils.toMap(ex.getFilteredAttributesList()));
      }
      rec.put("ExemplarsFilteredAttributes", exAttrs);
      rec.put("ExemplarsTimeUnix",           exTs);
      rec.put("ExemplarsValue",              exVal);
      rec.put("ExemplarsSpanId",             exSpan);
      rec.put("ExemplarsTraceId",            exTrace);
      out.add(rec);
    }
  }

  private static void mapExpHistogram(Metric metric, Map<String, String> ra, String resSchemaUrl,
      String scopeName, String scopeVersion, String scopeSchemaUrl,
      Schema schema, List<GenericRecord> out) {

    ExponentialHistogram expHist = metric.getExponentialHistogram();
    for (ExponentialHistogramDataPoint dp : expHist.getDataPointsList()) {
      Map<String, String> attrs = AttributeUtils.toMap(dp.getAttributesList());
      GenericRecord rec = new GenericData.Record(schema);
      putCommonMetricFields(rec, ra, resSchemaUrl, scopeName, scopeVersion, scopeSchemaUrl,
          attrs, metric.getName(), metric.getDescription(), metric.getUnit(),
          dp.getStartTimeUnixNano(), dp.getTimeUnixNano(), dp.getFlags());

      rec.put("Count",                  dp.getCount());
      rec.put("Sum",                    dp.hasSum() ? dp.getSum() : 0d);
      rec.put("Min",                    dp.hasMin() ? dp.getMin() : 0d);
      rec.put("Max",                    dp.hasMax() ? dp.getMax() : 0d);
      rec.put("Scale",                  dp.getScale());
      rec.put("ZeroCount",              dp.getZeroCount());
      rec.put("PositiveOffset",         dp.getPositive().getOffset());
      rec.put("PositiveBucketCounts",   dp.getPositive().getBucketCountsList());
      rec.put("NegativeOffset",         dp.getNegative().getOffset());
      rec.put("NegativeBucketCounts",   dp.getNegative().getBucketCountsList());
      rec.put("AggregationTemporality", expHist.getAggregationTemporalityValue());

      List<Long> exTs = new ArrayList<>();
      List<Double> exVal = new ArrayList<>();
      List<String> exSpan = new ArrayList<>();
      List<String> exTrace = new ArrayList<>();
      List<Map<String, String>> exAttrs = new ArrayList<>();
      for (var ex : dp.getExemplarsList()) {
        exTs.add(AttributeUtils.nanosToMicros(ex.getTimeUnixNano()));
        exVal.add(ex.hasAsDouble() ? ex.getAsDouble() : (double) ex.getAsInt());
        exSpan.add(AttributeUtils.bytesToHex(ex.getSpanId()));
        exTrace.add(AttributeUtils.bytesToHex(ex.getTraceId()));
        exAttrs.add(AttributeUtils.toMap(ex.getFilteredAttributesList()));
      }
      rec.put("ExemplarsFilteredAttributes", exAttrs);
      rec.put("ExemplarsTimeUnix",           exTs);
      rec.put("ExemplarsValue",              exVal);
      rec.put("ExemplarsSpanId",             exSpan);
      rec.put("ExemplarsTraceId",            exTrace);
      out.add(rec);
    }
  }

  private static void mapSummary(Metric metric, Map<String, String> ra, String resSchemaUrl,
      String scopeName, String scopeVersion, String scopeSchemaUrl,
      Schema schema, List<GenericRecord> out) {

    Summary summary = metric.getSummary();
    for (SummaryDataPoint dp : summary.getDataPointsList()) {
      Map<String, String> attrs = AttributeUtils.toMap(dp.getAttributesList());
      GenericRecord rec = new GenericData.Record(schema);
      putCommonMetricFields(rec, ra, resSchemaUrl, scopeName, scopeVersion, scopeSchemaUrl,
          attrs, metric.getName(), metric.getDescription(), metric.getUnit(),
          dp.getStartTimeUnixNano(), dp.getTimeUnixNano(), dp.getFlags());

      rec.put("Count", dp.getCount());
      rec.put("Sum",   dp.getSum());

      List<Double> quantiles = new ArrayList<>();
      List<Double> values = new ArrayList<>();
      for (SummaryDataPoint.ValueAtQuantile qv : dp.getQuantileValuesList()) {
        quantiles.add(qv.getQuantile());
        values.add(qv.getValue());
      }
      rec.put("ValueAtQuantilesQuantile", quantiles);
      rec.put("ValueAtQuantilesValue",    values);
      out.add(rec);
    }
  }
}
