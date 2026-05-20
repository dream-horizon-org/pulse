package org.dreamhorizon.pulseserver.grouping.phase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.FrameCategory;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;

/**
 * Phase 2e — waterfall frame selection.
 *
 * <p>Priority order:</p>
 * <ol>
 *   <li>All non-stripped {@link FrameCategory#IN_APP} frames, sorted by
 *       allowlist position then by original stack position.</li>
 *   <li>Else all non-stripped {@link FrameCategory#THIRD_PARTY} frames, same
 *       sort.</li>
 *   <li>Else the first {@code topN} non-stripped
 *       {@link FrameCategory#FRAMEWORK} frames in original stack order (the
 *       fallback case, tagged in metrics by {@link #wasFallback}).</li>
 * </ol>
 */
@UtilityClass
public class FrameSelector {

  private static final Comparator<Frame> BY_POSITION =
      Comparator.comparingInt(Frame::getCategoryRulePosition)
          .thenComparingInt(Frame::getOriginalPosition);

  /**
   * Run the waterfall against the primary lane's frames.
   *
   * @param frames the parsed (and already classified) frames container
   * @param lane the primary lane to draw from
   * @param topN max framework frames to keep in the fallback case
   * @return the chosen frame list (may be empty)
   */
  public static List<Frame> select(ParsedFrames frames, Lane lane, int topN) {
    if (frames == null || lane == null) {
      return List.of();
    }
    List<? extends Frame> laneFrames = framesForLane(frames, lane);
    if (laneFrames == null || laneFrames.isEmpty()) {
      return List.of();
    }

    List<Frame> inApp = collectCategory(laneFrames, FrameCategory.IN_APP);
    if (!inApp.isEmpty()) {
      inApp.sort(BY_POSITION);
      return inApp;
    }

    List<Frame> thirdParty = collectCategory(laneFrames, FrameCategory.THIRD_PARTY);
    if (!thirdParty.isEmpty()) {
      thirdParty.sort(BY_POSITION);
      return thirdParty;
    }

    List<Frame> framework = collectCategory(laneFrames, FrameCategory.FRAMEWORK);
    if (framework.size() > topN) {
      // framework frames keep original stack order; trim to topN
      return new ArrayList<>(framework.subList(0, topN));
    }
    return framework;
  }

  /**
   * True iff every frame in {@code selected} was classified as
   * {@link FrameCategory#FRAMEWORK}. Useful for metrics — a high fallback
   * rate signals the project's IN_APP / THIRD_PARTY allowlists need expansion.
   */
  public static boolean wasFallback(List<Frame> selected) {
    if (selected == null || selected.isEmpty()) {
      return false;
    }
    for (Frame f : selected) {
      if (f.getCategory() != FrameCategory.FRAMEWORK) {
        return false;
      }
    }
    return true;
  }

  private static List<Frame> collectCategory(List<? extends Frame> frames, FrameCategory cat) {
    List<Frame> out = new ArrayList<>();
    for (Frame f : frames) {
      if (f.isStripped()) {
        continue;
      }
      if (f.getCategory() == cat) {
        out.add(f);
      }
    }
    return out;
  }

  private static List<? extends Frame> framesForLane(ParsedFrames frames, Lane lane) {
    return switch (lane) {
      case JS -> frames.getJsFrames();
      case JAVA -> frames.getJavaFrames();
      case NDK -> frames.getNdkFrames();
      case IOS_NATIVE -> frames.getIosNativeFrames();
      default -> List.of();
    };
  }
}
