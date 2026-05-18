package org.dreamhorizon.pulses3archiver.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.io.InputStream;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TracesOtlpMapperTest {

  private static Schema schema;

  @BeforeAll
  static void loadSchema() throws Exception {
    try (InputStream is = TracesOtlpMapperTest.class.getClassLoader()
        .getResourceAsStream("schemas/otel_traces.avsc")) {
      schema = new Schema.Parser().parse(is);
    }
  }

  private static KeyValue kv(String key, String value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setStringValue(value).build())
        .build();
  }

  private ExportTraceServiceRequest buildRequest(String projectId, String sessionId,
      String pulseType, String platform, String appVersion) {
    Resource resource = Resource.newBuilder()
        .addAttributes(kv("project.id", projectId))
        .addAttributes(kv("os.name", platform))
        .addAttributes(kv("app.version", appVersion))
        .addAttributes(kv("service.name", "test-service"))
        .addAttributes(kv("device.model.identifier", "iPhone14"))
        .addAttributes(kv("telemetry.sdk.version", "1.2.3"))
        .build();

    Span span = Span.newBuilder()
        .setTraceId(ByteString.copyFrom(new byte[16]))
        .setSpanId(ByteString.copyFrom(new byte[8]))
        .setName("test-span")
        .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
        .setStartTimeUnixNano(1_700_000_000_000_000_000L)
        .setEndTimeUnixNano(1_700_000_001_000_000_000L)
        .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
        .addAttributes(kv("session.id", sessionId))
        .addAttributes(kv("pulse.type", pulseType))
        .addAttributes(kv("user.id", "user-123"))
        .addAttributes(kv("http.url", "https://api.example.com/v1/data"))
        .addAttributes(kv("http.method", "GET"))
        .addAttributes(kv("http.status_code", "200"))
        .addAttributes(kv("screen.name", "HomeScreen"))
        .build();

    ScopeSpans scopeSpans = ScopeSpans.newBuilder().addSpans(span).build();
    ResourceSpans resourceSpans = ResourceSpans.newBuilder()
        .setResource(resource)
        .addScopeSpans(scopeSpans)
        .build();

    return ExportTraceServiceRequest.newBuilder()
        .addResourceSpans(resourceSpans)
        .build();
  }

  @Test
  void shouldMapStoredColumns() {
    ExportTraceServiceRequest req = buildRequest("proj-1", "sess-abc", "network.200", "iOS", "2.0");
    List<GenericRecord> records = TracesOtlpMapper.map(req, schema);

    assertThat(records).hasSize(1);
    GenericRecord rec = records.get(0);

    assertThat(rec.get("SpanName")).hasToString("test-span");
    assertThat(rec.get("SpanKind")).hasToString("SPAN_KIND_CLIENT");
    assertThat(rec.get("ServiceName")).hasToString("test-service");
    assertThat(rec.get("StatusCode")).hasToString("STATUS_CODE_OK");
    // Duration = 1 second in nanos
    assertThat(rec.get("Duration")).isEqualTo(1_000_000_000L);
  }

  @Test
  void shouldPopulateMaterializedColumns() {
    ExportTraceServiceRequest req = buildRequest("proj-42", "sess-xyz", "screen_session", "Android", "3.1");
    List<GenericRecord> records = TracesOtlpMapper.map(req, schema);

    GenericRecord rec = records.get(0);

    assertThat(rec.get("ProjectId")).hasToString("proj-42");
    assertThat(rec.get("SessionId")).hasToString("sess-xyz");
    assertThat(rec.get("PulseType")).hasToString("screen_session");
    assertThat(rec.get("SpanType")).hasToString("screen_session");
    assertThat(rec.get("Platform")).hasToString("Android");
    assertThat(rec.get("AppVersion")).hasToString("3.1");
    assertThat(rec.get("DeviceModel")).hasToString("iPhone14");
    assertThat(rec.get("UserId")).hasToString("user-123");
    assertThat(rec.get("ScreenName")).hasToString("HomeScreen");
  }

  @Test
  void shouldPopulateHttpMaterializedColumns() {
    ExportTraceServiceRequest req = buildRequest("p", "s", "network.200", "iOS", "1.0");
    GenericRecord rec = TracesOtlpMapper.map(req, schema).get(0);

    assertThat(rec.get("HttpUrl")).hasToString("https://api.example.com/v1/data");
    assertThat(rec.get("HttpMethod")).hasToString("GET");
    assertThat((int) rec.get("HttpStatusCode")).isEqualTo(200);
  }

  @Test
  void shouldReturnEmptyListForNullRequest() {
    assertThat(TracesOtlpMapper.map(null, schema)).isEmpty();
  }

  @Test
  void shouldPopulateHourColumn() {
    ExportTraceServiceRequest req = buildRequest("p", "s", "t", "iOS", "1.0");
    GenericRecord rec = TracesOtlpMapper.map(req, schema).get(0);

    // 1_700_000_000_000_000_000 ns = 2023-11-14T22:13:20Z → hour=22
    assertThat((int) rec.get("Hour")).isEqualTo(22);
  }
}
