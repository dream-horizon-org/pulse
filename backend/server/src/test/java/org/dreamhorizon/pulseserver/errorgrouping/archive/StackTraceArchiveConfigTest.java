package org.dreamhorizon.pulseserver.errorgrouping.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StackTraceArchiveConfigTest {

  @Nested
  class Builder {

    @Test
    void shouldExposeAllFieldsViaBuilder() {
      StackTraceArchiveConfig config = StackTraceArchiveConfig.builder()
          .enabled(true)
          .s3Bucket("my-bucket")
          .s3Region("eu-west-1")
          .s3Endpoint("http://localhost:9100")
          .stagingDir("/tmp/staging")
          .flushIntervalMs(10_000L)
          .flushSizeBytes(100L)
          .flushAgeMs(200L)
          .rowGroupBytes(300L)
          .pageBytes(400L)
          .build();

      assertThat(config.isEnabled()).isTrue();
      assertThat(config.getS3Bucket()).isEqualTo("my-bucket");
      assertThat(config.getS3Region()).isEqualTo("eu-west-1");
      assertThat(config.getS3Endpoint()).isEqualTo("http://localhost:9100");
      assertThat(config.getStagingDir()).isEqualTo("/tmp/staging");
      assertThat(config.getFlushIntervalMs()).isEqualTo(10_000L);
      assertThat(config.getFlushSizeBytes()).isEqualTo(100L);
      assertThat(config.getFlushAgeMs()).isEqualTo(200L);
      assertThat(config.getRowGroupBytes()).isEqualTo(300L);
      assertThat(config.getPageBytes()).isEqualTo(400L);
    }
  }

  @Nested
  class FromEnvironment {

    @Test
    void shouldUseDefaultsWhenEnvUnset() {
      StackTraceArchiveConfig config = StackTraceArchiveConfig.fromEnvironment(Map.of());

      assertThat(config.isEnabled()).isFalse();
      assertThat(config.getS3Bucket()).isEqualTo("pulse-otel-ingestion");
      assertThat(config.getS3Region()).isEqualTo("ap-south-1");
      assertThat(config.getS3Endpoint()).isNull();
      assertThat(config.getStagingDir()).isEqualTo("/tmp/pulse-stack-trace-archive");
      assertThat(config.getFlushIntervalMs()).isEqualTo(30_000L);
      assertThat(config.getFlushSizeBytes()).isEqualTo(268_435_456L);
      assertThat(config.getFlushAgeMs()).isEqualTo(300_000L);
      assertThat(config.getRowGroupBytes()).isEqualTo(134_217_728L);
      assertThat(config.getPageBytes()).isEqualTo(1_048_576L);
    }

    @Test
    void shouldEnableWhenEnvTrue() {
      Map<String, String> env = Map.of("STACK_TRACE_S3_ARCHIVE_ENABLED", "true");

      assertThat(StackTraceArchiveConfig.fromEnvironment(env).isEnabled()).isTrue();
    }

    @Test
    void shouldTreatNonTrueEnabledValuesAsDisabled() {
      Map<String, String> env = Map.of("STACK_TRACE_S3_ARCHIVE_ENABLED", "yes");

      assertThat(StackTraceArchiveConfig.fromEnvironment(env).isEnabled()).isFalse();
    }

    @Test
    void shouldUseStackTraceOtelBucketWhenSet() {
      Map<String, String> env = Map.of("STACK_TRACE_OTEL_S3_BUCKET", "stack-trace-bucket");

      assertThat(StackTraceArchiveConfig.fromEnvironment(env).getS3Bucket()).isEqualTo("stack-trace-bucket");
    }

    @Test
    void shouldDefaultToPulseOtelIngestionWhenStackTraceBucketUnset() {
      assertThat(StackTraceArchiveConfig.fromEnvironment(Map.of()).getS3Bucket())
          .isEqualTo("pulse-otel-ingestion");
    }

    @Test
    void shouldParseNumericEnvOverrides() {
      Map<String, String> env = new HashMap<>();
      env.put("STACK_TRACE_ARCHIVE_FLUSH_INTERVAL_MS", "5000");
      env.put("STACK_TRACE_ARCHIVE_FLUSH_SIZE_BYTES", "1024");
      env.put("STACK_TRACE_ARCHIVE_FLUSH_AGE_MS", "60000");
      env.put("STACK_TRACE_ARCHIVE_ROW_GROUP_BYTES", "2048");
      env.put("STACK_TRACE_ARCHIVE_PAGE_BYTES", "512");

      StackTraceArchiveConfig config = StackTraceArchiveConfig.fromEnvironment(env);

      assertThat(config.getFlushIntervalMs()).isEqualTo(5000L);
      assertThat(config.getFlushSizeBytes()).isEqualTo(1024L);
      assertThat(config.getFlushAgeMs()).isEqualTo(60_000L);
      assertThat(config.getRowGroupBytes()).isEqualTo(2048L);
      assertThat(config.getPageBytes()).isEqualTo(512L);
    }

    @Test
    void shouldUseDefaultForInvalidNumericEnv() {
      Map<String, String> env = Map.of("STACK_TRACE_ARCHIVE_FLUSH_SIZE_BYTES", "not-a-number");

      assertThat(StackTraceArchiveConfig.fromEnvironment(env).getFlushSizeBytes()).isEqualTo(268_435_456L);
    }

    @Test
    void shouldTrimStringEnvValues() {
      Map<String, String> env = Map.of(
          "STACK_TRACE_ARCHIVE_STAGING_DIR", "  /var/staging  ",
          "S3_REGION", "  us-east-1  ");

      StackTraceArchiveConfig config = StackTraceArchiveConfig.fromEnvironment(env);

      assertThat(config.getStagingDir()).isEqualTo("/var/staging");
      assertThat(config.getS3Region()).isEqualTo("us-east-1");
    }
  }
}
