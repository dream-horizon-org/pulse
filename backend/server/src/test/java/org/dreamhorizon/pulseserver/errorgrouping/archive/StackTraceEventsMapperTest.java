package org.dreamhorizon.pulseserver.errorgrouping.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StackTraceEventsMapperTest {

  private static final DateTimeFormatter CH_TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS").withZone(ZoneOffset.UTC);

  private static Schema schema;

  @BeforeAll
  static void loadSchema() throws Exception {
    try (InputStream in = StackTraceEventsMapperTest.class.getClassLoader()
        .getResourceAsStream("schemas/stack_trace_events.avsc")) {
      schema = new Schema.Parser().parse(in);
    }
  }

  @Nested
  class ToRecord {

  @Test
  void shouldMapStoredAndMaterializedColumns() {
    Instant ts = Instant.parse("2026-05-18T12:34:56.123456789Z");
    StackTraceEvent event = StackTraceEvent.builder()
        .timestamp(CH_TS.format(ts))
        .eventName("device.crash")
        .pulseType("device.crash")
        .title("NullPointerException")
        .exceptionStackTrace("symbolicated")
        .exceptionStackTraceRaw("raw")
        .exceptionMessage("boom")
        .exceptionType("java.lang.NullPointerException")
        .interactions(List.of("Home"))
        .sessionId("sess-1")
        .groupId("EXC-abc")
        .signature("sig")
        .fingerprint("fp")
        .resourceAttributes(Map.of("project.id", "default-project"))
        .logAttributes(Map.of(
            "pulse.type", "device.crash",
            "pulse.metering.session.id", "meter-1",
            "app.installation.id", "install-1"))
        .build();

    GenericRecord rec = StackTraceEventsMapper.toRecord(event, schema);

    assertThat(rec.get("EventName")).isEqualTo("device.crash");
    assertThat(rec.get("PulseType")).isEqualTo("device.crash");
    assertThat(rec.get("ProjectId")).isEqualTo("default-project");
    assertThat(rec.get("MeteringSessionId")).isEqualTo("meter-1");
    assertThat(rec.get("AppInstallationId")).isEqualTo("install-1");
    assertThat(rec.get("GroupId")).isEqualTo("EXC-abc");
    assertThat((Long) rec.get("Timestamp")).isEqualTo(ts.getEpochSecond() * 1_000_000L + ts.getNano() / 1000L);
  }

  @Test
  void shouldDefaultNullStringsAndMapsToEmpty() {
    StackTraceEvent event = StackTraceEvent.builder()
        .timestamp("2026-05-18 12:00:00.000000000")
        .build();

    GenericRecord rec = StackTraceEventsMapper.toRecord(event, schema);

    assertThat(rec.get("EventName")).isEqualTo("");
    assertThat(rec.get("Title")).isEqualTo("");
    assertThat(rec.get("Interactions")).isEqualTo(List.of());
    assertThat(rec.get("ResourceAttributes")).isEqualTo(Map.of());
    assertThat(rec.get("LogAttributes")).isEqualTo(Map.of());
    assertThat(rec.get("ScopeAttributes")).isEqualTo(Map.of());
    assertThat(rec.get("ProjectId")).isEqualTo("");
    assertThat(rec.get("MeteringSessionId")).isEqualTo("");
    assertThat(rec.get("AppInstallationId")).isEqualTo("");
  }

  @Test
  void shouldPreferEventPulseTypeOverLogAttributes() {
    StackTraceEvent event = StackTraceEvent.builder()
        .timestamp("2026-05-18 12:00:00.000000000")
        .pulseType("device.crash")
        .logAttributes(Map.of("pulse.type", "anr"))
        .build();

    GenericRecord rec = StackTraceEventsMapper.toRecord(event, schema);

    assertThat(rec.get("PulseType")).isEqualTo("device.crash");
  }

  @Test
  void shouldFallBackToLogAttributePulseTypeWhenEventFieldBlank() {
    StackTraceEvent event = StackTraceEvent.builder()
        .timestamp("2026-05-18 12:00:00.000000000")
        .pulseType("  ")
        .logAttributes(Map.of("pulse.type", "anr"))
        .build();

    GenericRecord rec = StackTraceEventsMapper.toRecord(event, schema);

    assertThat(rec.get("PulseType")).isEqualTo("anr");
  }

  @Test
  void shouldDefaultPulseTypeToOtelWhenMissingEverywhere() {
    StackTraceEvent event = StackTraceEvent.builder()
        .timestamp("2026-05-18 12:00:00.000000000")
        .build();

    GenericRecord rec = StackTraceEventsMapper.toRecord(event, schema);

    assertThat(rec.get("PulseType")).isEqualTo("otel");
  }

  @Test
  void shouldCopyAllScalarFields() {
    StackTraceEvent event = StackTraceEvent.builder()
        .timestamp("2026-05-18 12:00:00.000000000")
        .screenName("Home")
        .userId("user-1")
        .sessionId("sess-1")
        .platform("android")
        .osVersion("14")
        .deviceModel("Pixel")
        .appVersionCode("100")
        .appVersion("1.0.0")
        .sdkVersion("2.0")
        .bundleId("com.example")
        .traceId("trace")
        .spanId("span")
        .signature("sig")
        .fingerprint("fp")
        .scopeAttributes(Map.of("scope.key", "scope-val"))
        .build();

    GenericRecord rec = StackTraceEventsMapper.toRecord(event, schema);

    assertThat(rec.get("ScreenName")).isEqualTo("Home");
    assertThat(rec.get("UserId")).isEqualTo("user-1");
    assertThat(rec.get("SessionId")).isEqualTo("sess-1");
    assertThat(rec.get("Platform")).isEqualTo("android");
    assertThat(rec.get("OsVersion")).isEqualTo("14");
    assertThat(rec.get("DeviceModel")).isEqualTo("Pixel");
    assertThat(rec.get("AppVersionCode")).isEqualTo("100");
    assertThat(rec.get("AppVersion")).isEqualTo("1.0.0");
    assertThat(rec.get("SdkVersion")).isEqualTo("2.0");
    assertThat(rec.get("BundleId")).isEqualTo("com.example");
    assertThat(rec.get("TraceId")).isEqualTo("trace");
    assertThat(rec.get("SpanId")).isEqualTo("span");
    assertThat(rec.get("Signature")).isEqualTo("sig");
    assertThat(rec.get("Fingerprint")).isEqualTo("fp");
    assertThat(rec.get("ScopeAttributes")).isEqualTo(Map.of("scope.key", "scope-val"));
  }

  }

  @Nested
  class ParseTimestampMicros {

  @Test
  void shouldParseClickHouseTimestampFormat() {
    String ts = "2026-05-18 12:34:56.123456789";
    long micros = StackTraceEventsMapper.parseTimestampMicros(ts);
    Instant expected = Instant.from(CH_TS.parse(ts));
    long expectedMicros = expected.getEpochSecond() * 1_000_000L + expected.getNano() / 1000L;
    assertThat(micros).isEqualTo(expectedMicros);
  }

  @Test
  void shouldTrimWhitespaceBeforeParsing() {
    String ts = "  2026-05-18 12:34:56.123456789  ";
    long micros = StackTraceEventsMapper.parseTimestampMicros(ts);
    Instant expected = Instant.from(CH_TS.parse(ts.trim()));
    long expectedMicros = expected.getEpochSecond() * 1_000_000L + expected.getNano() / 1000L;
    assertThat(micros).isEqualTo(expectedMicros);
  }

  @Test
  void shouldUseNowForNullOrBlankTimestamp() {
    long nowMicros = Instant.now().toEpochMilli() * 1000L;
    long toleranceMicros = 10_000_000L;

    assertThat(StackTraceEventsMapper.parseTimestampMicros(null))
        .isCloseTo(nowMicros, org.assertj.core.data.Offset.offset(toleranceMicros));
    assertThat(StackTraceEventsMapper.parseTimestampMicros("  "))
        .isCloseTo(nowMicros, org.assertj.core.data.Offset.offset(toleranceMicros));
  }

  @Test
  void shouldUseNowForUnparseableTimestamp() {
    long nowMicros = Instant.now().toEpochMilli() * 1000L;
    long micros = StackTraceEventsMapper.parseTimestampMicros("not-a-timestamp");

    assertThat(micros).isCloseTo(nowMicros, org.assertj.core.data.Offset.offset(10_000_000L));
  }

  }
}
