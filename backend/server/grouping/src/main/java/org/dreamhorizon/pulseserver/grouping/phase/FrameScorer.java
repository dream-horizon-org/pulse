package org.dreamhorizon.pulseserver.grouping.phase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.FrameCategory;

/**
 * Phase 3 — assign each selected frame a relevance score and return them
 * sorted high-to-low.
 *
 * <p>Formula (matches the heuristics spec Part VIII):</p>
 * <pre>
 *   score = tierWeight(category)
 *         + allowlistPositionBonus(categoryRulePosition)
 *         + depthWeight(originalPosition)
 *         + throwSiteBonus(first frame in its category? +50 : 0)
 *         + clarityBonus(token)
 * </pre>
 *
 * <p>Tie-break for equal scores: lower {@link Frame#getOriginalPosition()}
 * wins (closer to the throw site).</p>
 */
@UtilityClass
public class FrameScorer {

  // --- Tier weights (Phase 3, Part VIII) ---
  static final double IN_APP_WEIGHT = 250.0;
  static final double THIRD_PARTY_WEIGHT = 100.0;
  static final double FRAMEWORK_WEIGHT = 0.0;

  // --- Allowlist-position bonus (lower position = higher bonus) ---
  private static final double[] POSITION_BONUSES = {50.0, 30.0, 15.0, 10.0, 5.0};

  // --- Depth weight: top frame +100, decays by 10 per level, floor 0 ---
  private static final double DEPTH_WEIGHT_TOP = 100.0;
  private static final double DEPTH_WEIGHT_STEP = 10.0;

  // --- Throw-site bonus: first frame in its category ---
  private static final double THROW_SITE_BONUS = 50.0;

  // --- Clarity bonus / penalty on the token ---
  static final double CLARITY_BONUS = 20.0;
  static final double CLARITY_PENALTY = -20.0;

  /**
   * Score every frame in {@code frames} (in place, via
   * {@link Frame#setScore}) and return a new list sorted by descending score.
   * Ties broken by ascending {@link Frame#getOriginalPosition()}.
   */
  public static List<Frame> scoreAndSort(List<Frame> frames) {
    if (frames == null || frames.isEmpty()) {
      return List.of();
    }
    // first-frame-per-category tracking
    FrameCategory throwSiteCategory = frames.get(0).getCategory();
    int throwSiteOriginalPos = frames.get(0).getOriginalPosition();

    for (Frame f : frames) {
      double score = tierWeight(f.getCategory())
          + allowlistPositionBonus(f.getCategoryRulePosition())
          + depthWeight(f.getOriginalPosition())
          + throwSiteBonus(f, throwSiteCategory, throwSiteOriginalPos)
          + clarityBonus(f.getToken());
      f.setScore(score);
    }

    List<Frame> sorted = new ArrayList<>(frames);
    sorted.sort(
        Comparator.comparingDouble(Frame::getScore).reversed()
            .thenComparingInt(Frame::getOriginalPosition));
    return sorted;
  }

  static double tierWeight(FrameCategory category) {
    if (category == null) {
      return FRAMEWORK_WEIGHT;
    }
    return switch (category) {
      case IN_APP -> IN_APP_WEIGHT;
      case THIRD_PARTY -> THIRD_PARTY_WEIGHT;
      case FRAMEWORK -> FRAMEWORK_WEIGHT;
    };
  }

  static double allowlistPositionBonus(int position) {
    if (position < 0 || position >= POSITION_BONUSES.length) {
      return 0.0;
    }
    return POSITION_BONUSES[position];
  }

  static double depthWeight(int originalPosition) {
    if (originalPosition < 0) {
      return 0.0;
    }
    double w = DEPTH_WEIGHT_TOP - (originalPosition * DEPTH_WEIGHT_STEP);
    return Math.max(0.0, w);
  }

  private static double throwSiteBonus(Frame f, FrameCategory throwSiteCategory, int throwSitePos) {
    return (f.getCategory() == throwSiteCategory && f.getOriginalPosition() == throwSitePos)
        ? THROW_SITE_BONUS
        : 0.0;
  }

  static double clarityBonus(String token) {
    if (token == null || token.isEmpty()) {
      return 0.0;
    }
    if (hasSingleCharIdentifier(token)) {
      return CLARITY_PENALTY;
    }
    if (token.indexOf('.') >= 0) {
      return CLARITY_BONUS;
    }
    return 0.0;
  }

  /**
   * True if any dot/hash-separated identifier inside {@code token} is a single
   * character (typical R8/ProGuard obfuscation: {@code a.b.c#d}).
   */
  static boolean hasSingleCharIdentifier(String token) {
    int n = token.length();
    int start = 0;
    for (int i = 0; i <= n; i++) {
      char c = (i < n) ? token.charAt(i) : '.';
      if (c == '.' || c == '#') {
        if (i - start == 1) {
          return true;
        }
        start = i + 1;
      }
    }
    return false;
  }
}
