package org.dreamhorizon.pulses3archiver.service;

/** OTLP {@code resource project.id} → S3 bucket {@code pulse-otel-{suffix}} (AWS-safe). */
public final class ProjectBucketNames {

  /** When {@code project.id} is absent or sanitizes empty. */
  public static final String UNKNOWN_PROJECT_BUCKET = "pulse-otel-unknown";

  private static final String PREFIX = "pulse-otel-";

  /** Max chars after PREFIX (S3 DNS bucket labels ≤63 chars). */
  private static final int MAX_SUFFIX_CHARS = Math.max(0, 63 - PREFIX.length());

  private ProjectBucketNames() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** Converts raw OTLP project id attribute to bucket string. */
  public static String toBucket(CharSequence rawProjectId) {
    if (rawProjectId == null) {
      return UNKNOWN_PROJECT_BUCKET;
    }
    String sanitized = sanitizeSuffix(rawProjectId.toString());
    return sanitized.isEmpty() ? UNKNOWN_PROJECT_BUCKET : PREFIX + sanitized;
  }

  private static String sanitizeSuffix(String raw) {
    String t = raw.trim().toLowerCase().replace('_', '-').replace('.', '-');
    StringBuilder sb = new StringBuilder(Math.min(MAX_SUFFIX_CHARS, Math.max(t.length(), 16)));
    boolean lastDash = false;
    for (int i = 0; i < t.length(); i++) {
      if (sb.length() >= MAX_SUFFIX_CHARS) {
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
