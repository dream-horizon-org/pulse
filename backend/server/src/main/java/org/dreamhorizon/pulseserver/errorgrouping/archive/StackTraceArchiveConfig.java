package org.dreamhorizon.pulseserver.errorgrouping.archive;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StackTraceArchiveConfig {

  public static final String TABLE_STACK_TRACE_EVENTS = "stack_trace_events";

  boolean enabled;
  String s3Bucket;
  String s3Region;
  String s3Endpoint;
  String stagingDir;
  long flushIntervalMs;
  long flushSizeBytes;
  long flushAgeMs;
  long rowGroupBytes;
  long pageBytes;

  public static StackTraceArchiveConfig fromEnvironment() {
    return fromEnvironment(System.getenv());
  }

  public static StackTraceArchiveConfig fromEnvironment(Map<String, String> env) {
    String enabledEnv = env.get("STACK_TRACE_S3_ARCHIVE_ENABLED");
    boolean enabled = "true".equalsIgnoreCase(enabledEnv);
    String s3Endpoint = env.get("S3_ENDPOINT");
    return StackTraceArchiveConfig.builder()
        .enabled(enabled)
        .s3Bucket(envOr(env, "STACK_TRACE_OTEL_S3_BUCKET", "pulse-otel-ingestion"))
        .s3Region(envOr(env, "S3_REGION", "ap-south-1"))
        .s3Endpoint(s3Endpoint == null || s3Endpoint.isBlank() ? null : s3Endpoint.trim())
        .stagingDir(envOr(env, "STACK_TRACE_ARCHIVE_STAGING_DIR", "/tmp/pulse-stack-trace-archive"))
        .flushIntervalMs(longEnv(env, "STACK_TRACE_ARCHIVE_FLUSH_INTERVAL_MS", 30_000L))
        .flushSizeBytes(longEnv(env, "STACK_TRACE_ARCHIVE_FLUSH_SIZE_BYTES", 268_435_456L))
        .flushAgeMs(longEnv(env, "STACK_TRACE_ARCHIVE_FLUSH_AGE_MS", 300_000L))
        .rowGroupBytes(longEnv(env, "STACK_TRACE_ARCHIVE_ROW_GROUP_BYTES", 134_217_728L))
        .pageBytes(longEnv(env, "STACK_TRACE_ARCHIVE_PAGE_BYTES", 1_048_576L))
        .build();
  }

  private static String envOr(Map<String, String> env, String key, String defaultValue) {
    String v = env.get(key);
    return v == null || v.isBlank() ? defaultValue : v.trim();
  }

  private static long longEnv(Map<String, String> env, String key, long defaultValue) {
    String v = env.get(key);
    if (v == null || v.isBlank()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(v.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
