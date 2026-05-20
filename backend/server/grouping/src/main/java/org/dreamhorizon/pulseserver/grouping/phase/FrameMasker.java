package org.dreamhorizon.pulseserver.grouping.phase;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.MaskRule;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;

/**
 * Phase 1b — apply the per-project mask rules to every frame token (and to the
 * exception message, for Phase 4 fingerprint context).
 *
 * <p>Rules are applied in list order. Each rule is idempotent: running the
 * masker twice yields the same result.</p>
 */
@UtilityClass
public class FrameMasker {

  /**
   * Mutate every frame in {@code frames}: replace volatile substrings in the
   * token according to the mask rules. No-op when rules carry no
   * {@code maskRules}.
   */
  public static void maskFrames(ParsedFrames frames, GroupingRules rules) {
    if (frames == null || rules == null) {
      return;
    }
    List<MaskRule> maskRules = rules.getMaskRules();
    if (maskRules.isEmpty()) {
      return;
    }
    maskAll(frames.getJavaFrames(), maskRules);
    maskAll(frames.getJsFrames(), maskRules);
    maskAll(frames.getNdkFrames(), maskRules);
    maskAll(frames.getIosNativeFrames(), maskRules);
  }

  /**
   * Apply the same mask rules to a free-form exception message. Returns an
   * empty string when {@code exceptionMessage} is {@code null} so the
   * downstream signature builder always has a deterministic value to embed.
   */
  public static String maskMessage(String exceptionMessage, GroupingRules rules) {
    if (exceptionMessage == null) {
      return "";
    }
    if (rules == null || rules.getMaskRules().isEmpty()) {
      return exceptionMessage;
    }
    String out = exceptionMessage;
    for (MaskRule r : rules.getMaskRules()) {
      out = r.apply(out);
    }
    return out;
  }

  private static void maskAll(List<? extends Frame> frames, List<MaskRule> maskRules) {
    for (Frame f : frames) {
      String tok = f.getToken();
      if (tok == null) {
        continue;
      }
      String masked = tok;
      for (MaskRule r : maskRules) {
        masked = r.apply(masked);
      }
      f.setToken(masked);
    }
  }
}
