package org.dreamhorizon.pulseserver.grouping.phase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;

/**
 * Phase 1d — walk the {@code Caused by:} chain (Java/Kotlin) and surface the
 * root-cause exception type plus the frames that belong to it.
 *
 * <p>The parser already stores types in chain order — for Java the topmost
 * entry is the wrapper and the LAST entry is the root cause. This walker
 * consolidates that knowledge so downstream phases see the chain through a
 * single typed value ({@link RootCauseInfo}).</p>
 *
 * <p>For non-Java lanes there is no chain to walk: the result simply echoes
 * the lane's first type and all of its frames.</p>
 */
@UtilityClass
public class CausedByWalker {

  /**
   * Walk the exception chain for the given primary lane and return the root
   * cause type, the wrapper types (everything before the root cause), and the
   * frames that should feed the fingerprint.
   *
   * <p>Known limitation (documented in
   * {@code crash-grouping-algorithm-design/heuristics/index.md}): the parser
   * does not currently split frames per chain entry, so for Java we return
   * <em>all</em> java frames as the root-cause frames. Splitting will require
   * a parser-level change tracked separately.</p>
   */
  public static RootCauseInfo walk(ParsedFrames frames, Lane lane) {
    if (frames == null || lane == null) {
      return new RootCauseInfo(null, Collections.emptyList(), Collections.emptyList());
    }

    if (lane == Lane.JAVA) {
      List<String> types = frames.getJavaTypes();
      if (types == null || types.isEmpty()) {
        return new RootCauseInfo(null, Collections.emptyList(), frames.getJavaFrames());
      }
      String rootCauseType = types.get(types.size() - 1);
      List<String> wrappers = new ArrayList<>(types.subList(0, types.size() - 1));
      return new RootCauseInfo(rootCauseType, wrappers, frames.getJavaFrames());
    }

    List<String> types = typesForLane(frames, lane);
    String rootCauseType = (types == null || types.isEmpty()) ? null : types.get(0);
    List<? extends Frame> laneFrames = framesForLane(frames, lane);
    return new RootCauseInfo(rootCauseType, Collections.emptyList(), laneFrames);
  }

  private static List<String> typesForLane(ParsedFrames frames, Lane lane) {
    return switch (lane) {
      case JS -> frames.getJsTypes();
      case JAVA -> frames.getJavaTypes();
      case NDK -> frames.getNdkTypes();
      case IOS_NATIVE -> frames.getIosNativeTypes();
      default -> Collections.emptyList();
    };
  }

  private static List<? extends Frame> framesForLane(ParsedFrames frames, Lane lane) {
    return switch (lane) {
      case JS -> frames.getJsFrames();
      case JAVA -> frames.getJavaFrames();
      case NDK -> frames.getNdkFrames();
      case IOS_NATIVE -> frames.getIosNativeFrames();
      default -> Collections.emptyList();
    };
  }

  /**
   * Immutable result of a chain walk.
   */
  @Getter
  @ToString
  @RequiredArgsConstructor
  public static final class RootCauseInfo {
    private final String rootCauseType;
    private final List<String> wrapperTypes;
    private final List<? extends Frame> rootCauseFrames;

    /**
     * Convenience for the signature builder: returns wrappers (in chain
     * order) followed by the root cause as a flat list. When there is no
     * root cause type, returns an empty list.
     */
    public List<String> getAllTypesForSignature() {
      if (rootCauseType == null) {
        return Collections.emptyList();
      }
      List<String> all = new ArrayList<>(wrapperTypes.size() + 1);
      all.addAll(wrapperTypes);
      all.add(rootCauseType);
      return all;
    }
  }
}
