package org.dreamhorizon.pulses3archiver.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Histogram;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.metrics.v1.Summary;
import io.opentelemetry.proto.metrics.v1.SummaryDataPoint;
import io.opentelemetry.proto.resource.v1.Resource;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.dreamhorizon.pulses3archiver.mapper.MetricsOtlpMapper.MetricTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MetricsOtlpMapperTest {

  private static Map<MetricTable, Schema> schemas;

  @BeforeAll
  static void loadSchemas() throws Exception {
    schemas = new EnumMap<>(MetricTable.class);
    schemas.put(MetricTable.SUM, load("schemas/otel_metrics_sum.avsc"));
    schemas.put(MetricTable.HISTOGRAM, load("schemas/otel_metrics_histogram.avsc"));
    schemas.put(MetricTable.EXP_HISTOGRAM, load("schemas/otel_metrics_exp_histogram.avsc"));
    schemas.put(MetricTable.SUMMARY, load("schemas/otel_metrics_summary.avsc"));
  }

  private static Schema load(String path) throws Exception {
    try (InputStream is = MetricsOtlpMapperTest.class.getClassLoader().getResourceAsStream(path)) {
      return new Schema.Parser().parse(is);
    }
  }

  private static KeyValue kv(String key, String value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setStringValue(value).build())
        .build();
  }

  @Test
  void shouldFanOutSumIntoSumTable() {
    Metric metric = Metric.newBuilder()
        .setName("http.request.duration")
        .setUnit("ms")
        .setSum(Sum.newBuilder()
            .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA)
            .setIsMonotonic(true)
            .addDataPoints(NumberDataPoint.newBuilder()
                .setTimeUnixNano(1_700_000_000_000_000_000L)
                .setAsDouble(42.5)
                .build())
            .build())
        .build();

    ExportMetricsServiceRequest req = buildRequest(metric);
    Map<MetricTable, List<GenericRecord>> result = MetricsOtlpMapper.map(req, schemas);

    assertThat(result.get(MetricTable.SUM)).hasSize(1);
    assertThat(result.get(MetricTable.HISTOGRAM)).isEmpty();
    assertThat(result.get(MetricTable.EXP_HISTOGRAM)).isEmpty();
    assertThat(result.get(MetricTable.SUMMARY)).isEmpty();

    GenericRecord rec = result.get(MetricTable.SUM).get(0);
    assertThat(rec.get("MetricName")).hasToString("http.request.duration");
    assertThat((double) rec.get("Value")).isEqualTo(42.5);
    assertThat((boolean) rec.get("IsMonotonic")).isTrue();
  }

  @Test
  void shouldFanOutHistogramIntoHistogramTable() {
    Metric metric = Metric.newBuilder()
        .setName("http.response.size")
        .setHistogram(Histogram.newBuilder()
            .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
            .addDataPoints(HistogramDataPoint.newBuilder()
                .setTimeUnixNano(1_700_000_000_000_000_000L)
                .setCount(10)
                .setSum(500.0)
                .addBucketCounts(3)
                .addBucketCounts(7)
                .addExplicitBounds(100.0)
                .build())
            .build())
        .build();

    ExportMetricsServiceRequest req = buildRequest(metric);
    Map<MetricTable, List<GenericRecord>> result = MetricsOtlpMapper.map(req, schemas);

    assertThat(result.get(MetricTable.HISTOGRAM)).hasSize(1);
    assertThat(result.get(MetricTable.SUM)).isEmpty();

    GenericRecord rec = result.get(MetricTable.HISTOGRAM).get(0);
    assertThat(rec.get("MetricName")).hasToString("http.response.size");
    assertThat((long) rec.get("Count")).isEqualTo(10L);
    assertThat((double) rec.get("Sum")).isEqualTo(500.0);
  }

  @Test
  void shouldFanOutSummaryIntoSummaryTable() {
    SummaryDataPoint.ValueAtQuantile q50 = SummaryDataPoint.ValueAtQuantile.newBuilder()
        .setQuantile(0.5).setValue(100.0).build();
    SummaryDataPoint.ValueAtQuantile q99 = SummaryDataPoint.ValueAtQuantile.newBuilder()
        .setQuantile(0.99).setValue(900.0).build();

    Metric metric = Metric.newBuilder()
        .setName("latency.summary")
        .setSummary(Summary.newBuilder()
            .addDataPoints(SummaryDataPoint.newBuilder()
                .setTimeUnixNano(1_700_000_000_000_000_000L)
                .setCount(50)
                .setSum(5000.0)
                .addQuantileValues(q50)
                .addQuantileValues(q99)
                .build())
            .build())
        .build();

    ExportMetricsServiceRequest req = buildRequest(metric);
    Map<MetricTable, List<GenericRecord>> result = MetricsOtlpMapper.map(req, schemas);

    assertThat(result.get(MetricTable.SUMMARY)).hasSize(1);
    GenericRecord rec = result.get(MetricTable.SUMMARY).get(0);
    assertThat((long) rec.get("Count")).isEqualTo(50L);
    assertThat((double) rec.get("Sum")).isEqualTo(5000.0);

    @SuppressWarnings("unchecked")
    List<Double> quantiles = (List<Double>) rec.get("ValueAtQuantilesQuantile");
    assertThat(quantiles).containsExactly(0.5, 0.99);
  }

  @Test
  void shouldPopulateCommonMaterializedColumns() {
    Metric metric = Metric.newBuilder()
        .setName("test.metric")
        .setSum(Sum.newBuilder()
            .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA)
            .addDataPoints(NumberDataPoint.newBuilder()
                .setTimeUnixNano(1_700_000_000_000_000_000L)
                .setAsDouble(1.0)
                .addAttributes(kv("session.id", "sess-metric"))
                .build())
            .build())
        .build();

    ExportMetricsServiceRequest req = buildRequest(metric);
    GenericRecord rec = MetricsOtlpMapper.map(req, schemas).get(MetricTable.SUM).get(0);

    assertThat(rec.get("ProjectId")).hasToString("proj-metrics");
    assertThat(rec.get("Platform")).hasToString("iOS");
    assertThat(rec.get("SessionId")).hasToString("sess-metric");
  }

  @Test
  void shouldReturnEmptyListsForNullRequest() {
    Map<MetricTable, List<GenericRecord>> result = MetricsOtlpMapper.map(null, schemas);
    for (MetricTable t : MetricTable.values()) {
      assertThat(result.get(t)).isEmpty();
    }
  }

  private ExportMetricsServiceRequest buildRequest(Metric metric) {
    Resource resource = Resource.newBuilder()
        .addAttributes(kv("project.id", "proj-metrics"))
        .addAttributes(kv("os.name", "iOS"))
        .addAttributes(kv("service.name", "mobile-sdk"))
        .build();

    ScopeMetrics scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
    ResourceMetrics resourceMetrics = ResourceMetrics.newBuilder()
        .setResource(resource)
        .addScopeMetrics(scopeMetrics)
        .build();

    return ExportMetricsServiceRequest.newBuilder()
        .addResourceMetrics(resourceMetrics)
        .build();
  }
}
