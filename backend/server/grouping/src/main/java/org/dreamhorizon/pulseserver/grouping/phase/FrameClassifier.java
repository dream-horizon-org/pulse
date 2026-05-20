package org.dreamhorizon.pulseserver.grouping.phase;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.FrameCategory;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.JsFrame;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;

/**
 * Phase 2 — assign a {@link FrameCategory} (and a priority position) to every
 * frame.
 *
 * <p>Lookup order:</p>
 * <ol>
 *   <li>{@code inAppPrefixes} — first match wins; position 0 = highest
 *       priority within IN_APP.</li>
 *   <li>{@code thirdPartyPrefixes} — first match wins.</li>
 *   <li>{@code frameworkPrefixes} — first match wins.</li>
 *   <li>If still unmatched and {@code bundleIdFallback} is non-null and the
 *       package portion starts with it &rarr; IN_APP at lowest priority
 *       (position = {@code inAppPrefixes.size() + 1}).</li>
 *   <li>Default: FRAMEWORK with {@link Integer#MAX_VALUE} position.</li>
 * </ol>
 *
 * <p>For JS frames, prefixes that start with {@code /} match anywhere in the
 * file path (substring), everything else matches via {@code startsWith}. The
 * convention lets rules like {@code /node_modules/} live alongside file-path
 * roots such as {@code src/}.</p>
 */
@UtilityClass
public class FrameClassifier {

  /**
   * Walk every frame and assign category + rule position. Safe to call
   * multiple times — the result is deterministic for a given
   * (frame, rules) pair.
   */
  public static void classify(ParsedFrames frames, GroupingRules rules) {
    if (frames == null || rules == null) {
      return;
    }
    for (Frame f : frames.getJavaFrames()) {
      classifyOne(f, false, rules);
    }
    for (Frame f : frames.getJsFrames()) {
      classifyOne(f, true, rules);
    }
    for (Frame f : frames.getNdkFrames()) {
      classifyOne(f, false, rules);
    }
    for (Frame f : frames.getIosNativeFrames()) {
      classifyOne(f, false, rules);
    }
  }

  private static void classifyOne(Frame frame, boolean isJs, GroupingRules rules) {
    String token = frame.getToken();
    if (token == null) {
      assign(frame, FrameCategory.FRAMEWORK, Integer.MAX_VALUE);
      return;
    }
    String matchInput = isJs ? jsMatchInput(frame) : packagePart(token);

    int idx = matchIndex(matchInput, rules.getInAppPrefixes(), isJs);
    if (idx >= 0) {
      assign(frame, FrameCategory.IN_APP, idx);
      return;
    }
    idx = matchIndex(matchInput, rules.getThirdPartyPrefixes(), isJs);
    if (idx >= 0) {
      assign(frame, FrameCategory.THIRD_PARTY, idx);
      return;
    }
    idx = matchIndex(matchInput, rules.getFrameworkPrefixes(), isJs);
    if (idx >= 0) {
      assign(frame, FrameCategory.FRAMEWORK, idx);
      return;
    }

    // bundleId fallback — lowest priority within IN_APP
    String bundleId = rules.getBundleIdFallback();
    if (bundleId != null && !bundleId.isEmpty() && matchInput != null && matchInput.startsWith(bundleId)) {
      assign(frame, FrameCategory.IN_APP, rules.getInAppPrefixes().size() + 1);
      return;
    }

    assign(frame, FrameCategory.FRAMEWORK, Integer.MAX_VALUE);
  }

  /**
   * Match a single input against a prefix list. Returns the position of the
   * first matching prefix, or {@code -1} when none matched.
   */
  static int matchIndex(String input, List<String> prefixes, boolean isJs) {
    if (input == null) {
      return -1;
    }
    for (int i = 0; i < prefixes.size(); i++) {
      String p = prefixes.get(i);
      if (p == null || p.isEmpty()) {
        continue;
      }
      if (isJs && p.startsWith("/")) {
        if (input.contains(p)) {
          return i;
        }
      } else if (input.startsWith(p)) {
        return i;
      }
    }
    return -1;
  }

  /** Package portion of a Java/iOS token: everything before the first {@code #}. */
  static String packagePart(String token) {
    if (token == null) {
      return "";
    }
    int h = token.indexOf('#');
    return h >= 0 ? token.substring(0, h) : token;
  }

  /**
   * JS classification input. Prefer the raw file path (not the token) so that
   * substring rules like {@code /node_modules/} work against the full path.
   */
  static String jsMatchInput(Frame frame) {
    if (frame instanceof JsFrame js && js.getJsFile() != null) {
      return js.getJsFile().replace('\\', '/');
    }
    return packagePart(frame.getToken());
  }

  private static void assign(Frame f, FrameCategory category, int position) {
    f.setCategory(category);
    f.setCategoryRulePosition(position);
  }
}
