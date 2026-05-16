package org.dreamhorizon.pulses3archiver.service;

/**
 * Maps an OTLP {@code resource project.id} to a sanitized S3 key segment.
 *
 * <p>All projects share a single ingestion bucket; the prefix is the first path segment in the
 * object key (e.g. {@code s3://pulse-otel-ingestion/<prefix>/otel_traces/...}).
 *
 * <p>Bucket-label rules (DNS, ≤63 chars) do not apply to key segments, but we still produce
 * stable, ASCII-safe, lowercase segments to keep partitions readable and tooling-friendly.
 */
public final class ProjectKeyPrefixes {

  /** Used when {@code project.id} is absent, blank, or sanitizes to empty. */
  public static final String UNKNOWN_PROJECT_PREFIX = "unknown";

  /** Generous cap on the key segment length to keep keys readable. */
  private static final int MAX_PREFIX_CHARS = 128;

  private ProjectKeyPrefixes() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** Converts a raw OTLP project id attribute to the S3 key prefix segment. */
  public static String toPrefix(CharSequence rawProjectId) {
    if (rawProjectId == null) {
      return UNKNOWN_PROJECT_PREFIX;
    }
    String sanitized = sanitize(rawProjectId.toString());
    return sanitized.isEmpty() ? UNKNOWN_PROJECT_PREFIX : sanitized;
  }

  private static String sanitize(String raw) {
    String t = raw.trim().toLowerCase().replace('_', '-').replace('.', '-');
    StringBuilder sb = new StringBuilder(Math.min(MAX_PREFIX_CHARS, Math.max(t.length(), 16)));
    boolean lastDash = false;
    for (int i = 0; i < t.length(); i++) {
      if (sb.length() >= MAX_PREFIX_CHARS) {
        break;
      }
      char c = t.charAt(i);
      boolean digit = (c >= '0' && c <= '9');
      boolean letter = (c >= 'a' && c <= 'z');
      if (digit || letter) {
        sb.append(c);
        lastDash = false;
      } else if (c == '-') {
        if (!sb.isEmpty() && !lastDash) {
          sb.append('-');
          lastDash = true;
        }
      }
    }
    while (sb.length() > 0 && sb.charAt(0) == '-') {
      sb.deleteCharAt(0);
    }
    while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
      sb.setLength(sb.length() - 1);
    }
    return sb.toString();
  }
}
