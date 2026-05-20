package org.dreamhorizon.pulseserver.grouping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.grouping.model.EventMeta;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.Group;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.dreamhorizon.pulseserver.grouping.parser.FramesParser;
import org.dreamhorizon.pulseserver.grouping.phase.CausedByWalker;
import org.dreamhorizon.pulseserver.grouping.phase.FrameClassifier;
import org.dreamhorizon.pulseserver.grouping.phase.FrameMasker;
import org.dreamhorizon.pulseserver.grouping.phase.FrameScorer;
import org.dreamhorizon.pulseserver.grouping.phase.FrameSelector;
import org.dreamhorizon.pulseserver.grouping.phase.FrameStripper;
import org.dreamhorizon.pulseserver.grouping.phase.FrameUnifier;
import org.dreamhorizon.pulseserver.grouping.phase.SignatureBuilder;
import org.dreamhorizon.pulseserver.grouping.util.ErrorGroupingUtils;

/**
 * Pure heuristic core for crash/error grouping.
 *
 * <p>Given a {@link ParsedFrames} (produced by {@link FramesParser}), an
 * {@link EventMeta}, and a per-project {@link GroupingRules} bundle, computes a
 * deterministic {@link Group} — signature, fingerprint, groupId, and
 * human-readable display name. No I/O, no symbolication, no Guice. Every
 * input fully determines the output.</p>
 */
@UtilityClass
public class Grouper {

  // v1 was the legacy ErrorGroupingService inline algorithm. v2 is the pulse-grouping
  // module's canonical algorithm; the signature format gained a |msg: segment
  // when full Phase 4 (masked message context) landed. SIG_VERSION stays "v2"
  // — existing EXC- groupIds will be invalidated via a separate ClickHouse migration.
  public static final String SIG_VERSION = SignatureBuilder.SIG_VERSION;
  private static final String GROUP_ID_PREFIX = "EXC-";
  private static final int GROUP_ID_HASH_LEN = 10;

  /**
   * Primary entry point — runs every phase, in order, against the supplied
   * frames and per-project rules.
   */
  public static Group group(ParsedFrames frames, EventMeta meta, GroupingRules rules) {
    GroupingRules effectiveRules = rules == null ? GroupingRules.empty() : rules;

    Lane primary = choosePrimary(frames);

    // Phase 1: clean up
    FrameUnifier.unifyAll(frames);
    FrameMasker.maskFrames(frames, effectiveRules);
    FrameStripper.stripFrames(frames, effectiveRules);

    // Phase 2: classify + select
    FrameClassifier.classify(frames, effectiveRules);
    CausedByWalker.RootCauseInfo rootCause = CausedByWalker.walk(frames, primary);

    List<Frame> selected = FrameSelector.select(frames, primary, FramesParser.TOP_N_FRAMES);

    // Phase 3: score + rank
    List<Frame> ranked = FrameScorer.scoreAndSort(selected);

    // Phase 4: build signature
    String maskedMsg = FrameMasker.maskMessage(frames.getExceptionHeaderLine(), effectiveRules);
    String platformTag = ErrorGroupingUtils.platformTag(primary);
    List<String> tokens = new ArrayList<>(ranked.size());
    for (Frame f : ranked) {
      tokens.add(f.getToken());
    }
    List<String> excTypes = rootCause.getAllTypesForSignature();
    if (excTypes.isEmpty()) {
      // fall back to whatever the parser captured on the primary lane (or any lane)
      excTypes = typesForPrimary(frames, primary);
    }

    String signature = SignatureBuilder.build(platformTag, excTypes, tokens, maskedMsg);
    String fingerprint = ErrorGroupingUtils.sha1Hex(signature);
    String groupId = computeGroupId(fingerprint);
    String displayName = buildDisplayName(primary, excTypes, tokens, groupId);
    return new Group(platformTag, signature, fingerprint, groupId, displayName);
  }

  /**
   * Backwards-compatible convenience: invokes {@link #group(ParsedFrames, EventMeta, GroupingRules)}
   * with {@link GroupingRules#empty()}. Existing callers / tests that have no
   * rule bundle yet keep compiling; once they migrate this overload can go
   * away.
   */
  public static Group group(ParsedFrames frames, EventMeta meta) {
    return group(frames, meta, GroupingRules.empty());
  }

  /**
   * Legacy signature builder shape (no message). Delegates to
   * {@link SignatureBuilder#build(String, List, List, String)} with an empty
   * message. Kept for backwards compatibility with existing test code; new
   * code should call {@link SignatureBuilder#build} directly.
   */
  public static String buildSignature(String platform, List<String> excTypes, List<String> tokens) {
    return SignatureBuilder.build(platform, excTypes, tokens, "");
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
   * Legacy frame selector kept for callers that haven't migrated yet. Runs a
   * one-off classification using empty rules (so every frame defaults to
   * FRAMEWORK), then delegates to {@link FrameSelector#select} which falls
   * through to the top-N FRAMEWORK path. New code should call
   * {@link FrameClassifier} + {@link FrameSelector} directly with real rules.
   */
  public static List<Frame> selectPrimaryTokens(ParsedFrames st, Lane lane, int topN) {
    FrameClassifier.classify(st, GroupingRules.empty());
    return FrameSelector.select(st, lane, topN);
  }
}
