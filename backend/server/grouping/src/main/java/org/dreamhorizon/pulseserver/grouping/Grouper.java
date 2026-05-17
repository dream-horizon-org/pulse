package org.dreamhorizon.pulseserver.grouping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.grouping.model.EventMeta;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.Group;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.dreamhorizon.pulseserver.grouping.parser.FramesParser;
import org.dreamhorizon.pulseserver.grouping.util.ErrorGroupingUtils;

/**
 * Pure heuristic core for crash/error grouping.
 *
 * <p>Given a {@link ParsedFrames} (produced by {@link FramesParser}) and a small
 * {@link EventMeta}, computes a deterministic {@link Group} — signature, fingerprint,
 * groupId, and human-readable display name. No I/O, no symbolication. All inputs
 * fully determine the output.</p>
 */
@UtilityClass
public class Grouper {

  public static final String SIG_VERSION = "v1";
  private static final String GROUP_ID_PREFIX = "EXC-";
  private static final int GROUP_ID_HASH_LEN = 10;

  /**
   * Top-level convenience: parse → choose lane → build signature/groupId/title.
   *
   * <p>Tokens for the signature come straight from {@link Frame#getToken()} —
   * no symbolication is applied here. Callers needing symbolication should
   * transform frame tokens before invoking the lower-level builders.</p>
   */
  public static Group group(ParsedFrames frames, EventMeta meta) {
    Lane primary = choosePrimary(frames);
    List<String> excTypes = typesForPrimary(frames, primary);
    List<Frame> primaryFrames = selectPrimaryTokens(frames, primary, FramesParser.TOP_N_FRAMES);
    List<String> tokens = new ArrayList<>(primaryFrames.size());
    for (Frame f : primaryFrames) {
      tokens.add(f.getToken());
    }
    String platformTag = ErrorGroupingUtils.platformTag(primary);
    String signature = buildSignature(platformTag, excTypes, tokens);
    String fingerprint = ErrorGroupingUtils.sha1Hex(signature);
    String groupId = computeGroupId(fingerprint);
    String displayName = buildDisplayName(primary, excTypes, tokens, groupId);
    return new Group(platformTag, signature, fingerprint, groupId, displayName);
  }

  /**
   * Build the canonical signature string fed into the SHA-1 fingerprint.
   * Format: {@code v1|platform:<tag>|exc:<type>(>type)*|frames:<token>(>token)*}.
   */
  public static String buildSignature(String platform, List<String> excTypes, List<String> tokens) {
    int capacity = 50 + platform.length() + excTypes.size() * 20 + tokens.size() * 30;
    StringBuilder sb = new StringBuilder(capacity);

    sb.append(SIG_VERSION).append("|platform:").append(platform).append("|exc:");
    for (int i = 0; i < excTypes.size(); i++) {
      if (i > 0) {
        sb.append(">");
      }
      sb.append(excTypes.get(i));
    }

    sb.append("|frames:");
    for (int i = 0; i < tokens.size(); i++) {
      if (i > 0) {
        sb.append(">");
      }
      sb.append(tokens.get(i));
    }
    return sb.toString();
  }

  /**
   * Compute the public {@code EXC-XXXXXXXXXX} groupId from a SHA-1 fingerprint
   * (10-char uppercase prefix).
   */
  public static String computeGroupId(String fingerprintSha1Hex) {
    if (fingerprintSha1Hex == null || fingerprintSha1Hex.length() < GROUP_ID_HASH_LEN) {
      throw new IllegalArgumentException("fingerprint must be a SHA-1 hex string of at least "
          + GROUP_ID_HASH_LEN + " chars");
    }
    return GROUP_ID_PREFIX + fingerprintSha1Hex.substring(0, GROUP_ID_HASH_LEN).toUpperCase(Locale.ROOT);
  }

  /**
   * Build the user-facing title for the group, e.g.
   * {@code NullPointerException at MyActivity#onCreate [EXC-ABCDEF1234]}.
   */
  public static String buildDisplayName(Lane lane, List<String> excTypes, List<String> frames, String groupId) {
    String headline;
    if (excTypes.isEmpty()) {
      headline = (lane == Lane.NDK || lane == Lane.IOS_NATIVE) ? "NativeError" : "Error";
    } else if (lane == Lane.JAVA && excTypes.size() >= 2) {
      headline = excTypes.get(0) + " caused by " + excTypes.get(excTypes.size() - 1);
    } else {
      headline = excTypes.get(0);
    }

    String loc = frames.isEmpty() ? "" : frames.get(0);
    String locPretty = switch (lane) {
      case JAVA -> ErrorGroupingUtils.shortenJava(loc);
      case JS -> ErrorGroupingUtils.shortenJs(loc);
      default -> loc;
    };

    String where = locPretty.isEmpty() ? "" :
        (lane == Lane.JS ? " in " : " at ") + locPretty;

    return headline + where + " [" + groupId + "]";
  }

  /**
   * Choose the primary lane (JS / JAVA / NDK / IOS_NATIVE) for the group.
   * Uses the topmost exception lane if present, falling back to frame counts.
   */
  public static Lane choosePrimary(ParsedFrames st) {
    int js = st.getJsFrames().size();
    int jv = st.getJavaFrames().size();
    int nk = st.getNdkFrames().size();
    int io = st.getIosNativeFrames().size();

    if (js == 0 && jv == 0 && nk == 0 && io == 0) {
      return Lane.UNKNOWN;
    }

    if (st.getPrimaryExceptionLane() != null) {
      Lane primary = st.getPrimaryExceptionLane();
      if (primary == Lane.JS && js > 0) {
        return Lane.JS;
      }
      if (primary == Lane.JAVA && jv > 0) {
        return Lane.JAVA;
      }
      if (primary == Lane.NDK && nk > 0) {
        return Lane.NDK;
      }
      if (primary == Lane.IOS_NATIVE && io > 0) {
        return Lane.IOS_NATIVE;
      }
    }

    int max = Math.max(js, Math.max(jv, Math.max(nk, io)));
    if (js == max) {
      return Lane.JS;
    }
    if (jv == max) {
      return Lane.JAVA;
    }
    if (io == max) {
      return Lane.IOS_NATIVE;
    }
    return Lane.NDK;
  }

  /**
   * Exception types for the primary lane, with a fallback chain to other lanes
   * if the primary lane has no types.
   */
  public static List<String> typesForPrimary(ParsedFrames st, Lane lane) {
    List<String> types = switch (lane) {
      case JS -> st.getJsTypes();
      case JAVA -> st.getJavaTypes();
      case NDK -> st.getNdkTypes();
      case IOS_NATIVE -> st.getIosNativeTypes();
      default -> List.of();
    };
    if (types == null || types.isEmpty()) {
      if (!st.getJsTypes().isEmpty()) {
        return st.getJsTypes();
      }
      if (!st.getJavaTypes().isEmpty()) {
        return st.getJavaTypes();
      }
      if (!st.getIosNativeTypes().isEmpty()) {
        return st.getIosNativeTypes();
      }
      if (!st.getNdkFrames().isEmpty()) {
        return st.getNdkTypes();
      }
      return List.of();
    }
    return types;
  }

  /**
   * Pick up to {@code topN} frames from the primary lane, preferring in-app frames.
   * If no in-app frames exist, falls back to the first {@code topN} of any type.
   */
  public static List<Frame> selectPrimaryTokens(ParsedFrames st, Lane lane, int topN) {
    List<? extends Frame> frames = switch (lane) {
      case JS -> st.getJsFrames();
      case JAVA -> st.getJavaFrames();
      case NDK -> st.getNdkFrames();
      case IOS_NATIVE -> st.getIosNativeFrames();
      default -> List.of();
    };
    if (frames.isEmpty()) {
      return List.of();
    }

    List<Frame> chosen = new ArrayList<>(topN);
    for (Frame f : frames) {
      if (f.isInApp()) {
        chosen.add(f);
        if (chosen.size() == topN) {
          break;
        }
      }
    }
    if (chosen.isEmpty()) {
      for (Frame f : frames) {
        chosen.add(f);
        if (chosen.size() == topN) {
          break;
        }
      }
    }
    return chosen;
  }
}
