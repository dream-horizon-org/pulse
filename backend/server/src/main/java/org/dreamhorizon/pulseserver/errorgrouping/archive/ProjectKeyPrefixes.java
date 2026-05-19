package org.dreamhorizon.pulseserver.errorgrouping.archive;

/**
 * Maps a raw project id to a sanitized S3 key prefix segment (same rules as pulse-s3-archiver).
 */
public final class ProjectKeyPrefixes {

  public static final String UNKNOWN_PROJECT_PREFIX = "unknown";
  private static final int MAX_PREFIX_CHARS = 128;

  private ProjectKeyPrefixes() {
    throw new UnsupportedOperationException("Utility class");
  }

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
