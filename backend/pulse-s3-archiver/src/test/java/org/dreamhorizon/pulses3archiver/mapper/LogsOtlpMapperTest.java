package org.dreamhorizon.pulses3archiver.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.resource.v1.Resource;
import java.io.InputStream;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LogsOtlpMapperTest {

  private static Schema schema;

  @BeforeAll
  static void loadSchema() throws Exception {
    try (InputStream is = LogsOtlpMapperTest.class.getClassLoader()
        .getResourceAsStream("schemas/otel_logs.avsc")) {
      schema = new Schema.Parser().parse(is);
    }
  }

  private static KeyValue kv(String key, String value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setStringValue(value).build())
        .build();
  }

  private ExportLogsServiceRequest buildRequest(String pulseType, String body) {
    Resource resource = Resource.newBuilder()
        .addAttributes(kv("project.id", "proj-logs"))
        .addAttributes(kv("os.name", "web"))
        .addAttributes(kv("service.name", "web-sdk"))
        .addAttributes(kv("app.build_name", "1.0.0"))
        .build();

    LogRecord.Builder lr = LogRecord.newBuilder()
        .setTimeUnixNano(1_700_000_000_000_000_000L)
        .setTraceId(ByteString.copyFrom(new byte[16]))
        .setSpanId(ByteString.copyFrom(new byte[8]))
        .setBody(AnyValue.newBuilder().setStringValue(body).build())
        .addAttributes(kv("session.id", "sess-web-1"))
        .addAttributes(kv("user.id", "user-web-1"))
        .addAttributes(kv("screen.name", "CheckoutPage"))
        .addAttributes(kv("geo.country.iso_code", "IN"));

    if (pulseType != null) {
      lr.addAttributes(kv("pulse.type", pulseType));
    }

    ScopeLogs scopeLogs = ScopeLogs.newBuilder().addLogRecords(lr.build()).build();
    ResourceLogs resourceLogs = ResourceLogs.newBuilder()
        .setResource(resource)
        .addScopeLogs(scopeLogs)
        .build();

    return ExportLogsServiceRequest.newBuilder().addResourceLogs(resourceLogs).build();
  }

  @Test
  void shouldMapMaterializedColumns() {
    List<GenericRecord> records = LogsOtlpMapper.map(buildRequest("app.click", "click"), schema);

    assertThat(records).hasSize(1);
    GenericRecord rec = records.get(0);

    assertThat(rec.get("ProjectId")).hasToString("proj-logs");
    assertThat(rec.get("SessionId")).hasToString("sess-web-1");
    assertThat(rec.get("UserId")).hasToString("user-web-1");
    assertThat(rec.get("Platform")).hasToString("web");
    assertThat(rec.get("ScreenName")).hasToString("CheckoutPage");
    assertThat(rec.get("GeoCountry")).hasToString("IN");
    assertThat(rec.get("PulseType")).hasToString("app.click");
  }

  @Test
  void shouldDefaultPulseTypeToOtelWhenAbsent() {
    GenericRecord rec = LogsOtlpMapper.map(buildRequest(null, "raw log"), schema).get(0);
    assertThat(rec.get("PulseType")).hasToString("otel");
  }

  @Test
  void shouldDefaultPulseTypeToOtelWhenEmpty() {
    GenericRecord rec = LogsOtlpMapper.map(buildRequest("", "raw log"), schema).get(0);
    assertThat(rec.get("PulseType")).hasToString("otel");
  }

  @Test
  void shouldSetEventNameForCustomEvent() {
    GenericRecord rec = LogsOtlpMapper.map(
        buildRequest("custom_event", "user_signup"), schema).get(0);
    assertThat(rec.get("EventName")).hasToString("user_signup");
  }

  @Test
  void shouldSetEmptyEventNameForNonCustomEvent() {
    GenericRecord rec = LogsOtlpMapper.map(
        buildRequest("app.click", "ignored-body"), schema).get(0);
    assertThat(rec.get("EventName")).hasToString("");
  }

  @Test
  void shouldReturnEmptyListForNullRequest() {
    assertThat(LogsOtlpMapper.map(null, schema)).isEmpty();
  }
}
