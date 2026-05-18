package org.dreamhorizon.pulseserver.errorgrouping.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StackTraceArchiveConfigTest {

  private static final String[] MANAGED_KEYS = {
      "STACK_TRACE_S3_ARCHIVE_ENABLED",
      "STACK_TRACE_OTEL_S3_BUCKET",
      "S3_REGION",
      "S3_ENDPOINT",
      "STACK_TRACE_ARCHIVE_STAGING_DIR",
      "STACK_TRACE_ARCHIVE_FLUSH_INTERVAL_MS",
      "STACK_TRACE_ARCHIVE_FLUSH_SIZE_BYTES",
      "STACK_TRACE_ARCHIVE_FLUSH_AGE_MS",
      "STACK_TRACE_ARCHIVE_ROW_GROUP_BYTES",
      "STACK_TRACE_ARCHIVE_PAGE_BYTES"
  };

  private final Map<String, String> saved = new HashMap<>();

  @BeforeEach
  void snapshotEnv() {
    for (String key : MANAGED_KEYS) {
      String v = System.getenv(key);
      if (v != null) {
        saved.put(key, v);
      }
    }
  }

  @AfterEach
  void restoreEnv() throws Exception {
    for (String key : MANAGED_KEYS) {
      if (saved.containsKey(key)) {
        setEnv(key, saved.get(key));
      } else {
        unsetEnv(key);
      }
    }
    saved.clear();
  }

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
    void shouldUseDefaultsWhenEnvUnset() throws Exception {
      for (String key : MANAGED_KEYS) {
        unsetEnv(key);
      }

      StackTraceArchiveConfig config = StackTraceArchiveConfig.fromEnvironment();

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
    void shouldEnableWhenEnvTrue() throws Exception {
      setEnv("STACK_TRACE_S3_ARCHIVE_ENABLED", "true");

      assertThat(StackTraceArchiveConfig.fromEnvironment().isEnabled()).isTrue();
    }

    @Test
    void shouldTreatNonTrueEnabledValuesAsDisabled() throws Exception {
      setEnv("STACK_TRACE_S3_ARCHIVE_ENABLED", "yes");

      assertThat(StackTraceArchiveConfig.fromEnvironment().isEnabled()).isFalse();
    }

    @Test
    void shouldUseStackTraceOtelBucketWhenSet() throws Exception {
      setEnv("STACK_TRACE_OTEL_S3_BUCKET", "stack-trace-bucket");

      assertThat(StackTraceArchiveConfig.fromEnvironment().getS3Bucket()).isEqualTo("stack-trace-bucket");
    }

    @Test
    void shouldDefaultToPulseOtelIngestionWhenStackTraceBucketUnset() throws Exception {
      unsetEnv("STACK_TRACE_OTEL_S3_BUCKET");

      assertThat(StackTraceArchiveConfig.fromEnvironment().getS3Bucket()).isEqualTo("pulse-otel-ingestion");
    }

    @Test
    void shouldParseNumericEnvOverrides() throws Exception {
      setEnv("STACK_TRACE_ARCHIVE_FLUSH_INTERVAL_MS", "5000");
      setEnv("STACK_TRACE_ARCHIVE_FLUSH_SIZE_BYTES", "1024");
      setEnv("STACK_TRACE_ARCHIVE_FLUSH_AGE_MS", "60000");
      setEnv("STACK_TRACE_ARCHIVE_ROW_GROUP_BYTES", "2048");
      setEnv("STACK_TRACE_ARCHIVE_PAGE_BYTES", "512");

      StackTraceArchiveConfig config = StackTraceArchiveConfig.fromEnvironment();

      assertThat(config.getFlushIntervalMs()).isEqualTo(5000L);
      assertThat(config.getFlushSizeBytes()).isEqualTo(1024L);
      assertThat(config.getFlushAgeMs()).isEqualTo(60_000L);
      assertThat(config.getRowGroupBytes()).isEqualTo(2048L);
      assertThat(config.getPageBytes()).isEqualTo(512L);
    }

    @Test
    void shouldUseDefaultForInvalidNumericEnv() throws Exception {
      setEnv("STACK_TRACE_ARCHIVE_FLUSH_SIZE_BYTES", "not-a-number");

      assertThat(StackTraceArchiveConfig.fromEnvironment().getFlushSizeBytes()).isEqualTo(268_435_456L);
    }

    @Test
    void shouldTrimStringEnvValues() throws Exception {
      setEnv("STACK_TRACE_ARCHIVE_STAGING_DIR", "  /var/staging  ");
      setEnv("S3_REGION", "  us-east-1  ");

      StackTraceArchiveConfig config = StackTraceArchiveConfig.fromEnvironment();

      assertThat(config.getStagingDir()).isEqualTo("/var/staging");
      assertThat(config.getS3Region()).isEqualTo("us-east-1");
    }
  }

  @SuppressWarnings("unchecked")
  private static void setEnv(String key, String value) throws Exception {
    Map<String, String> env = System.getenv();
    Field field = env.getClass().getDeclaredField("m");
    field.setAccessible(true);
    Map<String, String> writable = (Map<String, String>) field.get(env);
    writable.put(key, value);
  }

  @SuppressWarnings("unchecked")
  private static void unsetEnv(String key) throws Exception {
    Map<String, String> env = System.getenv();
    Field field = env.getClass().getDeclaredField("m");
    field.setAccessible(true);
    Map<String, String> writable = (Map<String, String>) field.get(env);
    writable.remove(key);
  }
}
