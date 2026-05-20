package org.dreamhorizon.pulseserver.grouping.phase;

import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;

/**
 * Phase 1c — tag frames matching the "universal noise" strip catalog.
 *
 * <p>Tag-don't-delete: every matching frame's {@link Frame#setStripped(boolean)}
 * flag is set to {@code true}, but the frame stays in {@link ParsedFrames} so
 * it remains visible for debugging and display. The selector skips stripped
 * frames when assembling the fingerprint input.</p>
 *
 * <p>Idempotent — running the stripper twice produces the same result.</p>
 */
@UtilityClass
public class FrameStripper {

  /**
   * Mark every frame whose {@code token} matches any of {@code rules.stripPatterns}
   * as stripped. No-op when there are no patterns configured.
   */
  public static void stripFrames(ParsedFrames frames, GroupingRules rules) {
    if (frames == null || rules == null) {
      return;
    }
    List<Pattern> patterns = rules.getStripPatterns();
    if (patterns.isEmpty()) {
      return;
    }
    tagAll(frames.getJavaFrames(), patterns);
    tagAll(frames.getJsFrames(), patterns);
    tagAll(frames.getNdkFrames(), patterns);
    tagAll(frames.getIosNativeFrames(), patterns);
  }

  private static void tagAll(List<? extends Frame> frames, List<Pattern> patterns) {
    for (Frame f : frames) {
      String tok = f.getToken();
      if (tok == null) {
        continue;
      }
      for (Pattern p : patterns) {
        if (p.matcher(tok).matches()) {
          f.setStripped(true);
          break;
        }
      }
    }
  }
}
