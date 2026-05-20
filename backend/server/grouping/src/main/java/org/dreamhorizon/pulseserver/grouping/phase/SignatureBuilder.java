package org.dreamhorizon.pulseserver.grouping.phase;

import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Phase 4 — build the canonical signature string that feeds the SHA-1
 * fingerprint.
 *
 * <p>Format:</p>
 * <pre>
 *   v2|platform:&lt;tag&gt;|exc:&lt;type1&gt;&gt;&lt;type2&gt;…|frames:&lt;f1&gt;&gt;&lt;f2&gt;…|msg:&lt;maskedMessage&gt;
 * </pre>
 *
 * <p>All four segments are always present even when their content is empty —
 * keeping the shape stable across events makes the fingerprint deterministic
 * and the format scriptable.</p>
 */
@UtilityClass
public class SignatureBuilder {

  /**
   * Algorithm version embedded in every signature. v1 was the legacy inline
   * implementation; v2 is the pulse-grouping module's canonical algorithm.
   * Do not bump this without coordinating a ClickHouse migration — every
   * existing {@code EXC-} groupId is keyed off this prefix.
   */
  public static final String SIG_VERSION = "v2";

  /**
   * Build the canonical signature string for the given inputs. All four
   * segments are always present (empty values produce e.g. {@code |msg:}).
   */
  public static String build(String platform,
                             List<String> excTypes,
                             List<String> frameTokens,
                             String maskedMessage) {
    String platformValue = platform == null ? "" : platform;
    String msgValue = maskedMessage == null ? "" : maskedMessage;

    int capacity = 60 + platformValue.length() + msgValue.length()
        + (excTypes == null ? 0 : excTypes.size() * 20)
        + (frameTokens == null ? 0 : frameTokens.size() * 30);
    StringBuilder sb = new StringBuilder(capacity);

    sb.append(SIG_VERSION).append("|platform:").append(platformValue).append("|exc:");
    appendJoined(sb, excTypes);

    sb.append("|frames:");
    appendJoined(sb, frameTokens);

    sb.append("|msg:").append(msgValue);
    return sb.toString();
  }

  private static void appendJoined(StringBuilder sb, List<String> items) {
    if (items == null || items.isEmpty()) {
      return;
    }
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        sb.append('>');
      }
      String v = items.get(i);
      if (v != null) {
        sb.append(v);
      }
    }
  }
}
