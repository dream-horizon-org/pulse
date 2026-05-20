package org.dreamhorizon.pulseserver.grouping.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public abstract class Frame {
  protected Lane lane;
  // Common normalized token after (optional) symbolication
  protected String token;     // java: pkg.Class#method ; js: file#function ; ndk: lib.so#symbol

  /**
   * Legacy binary in-app flag set at parse time. New code should consult
   * {@link #category} instead — it is set by the Phase 2 classifier and
   * carries IN_APP / THIRD_PARTY / FRAMEWORK information. Kept for backwards
   * compatibility with existing tests and callers; will be removed once those
   * migrate.
   *
   * @deprecated use {@link #category} (populated by FrameClassifier).
   */
  @Deprecated
  protected boolean inApp;
  protected String rawLine;
  // Track original position in stack trace for reconstruction after symbolication
  protected int originalPosition = -1;

  /**
   * Category assigned by Phase 2 (FrameClassifier). {@code null} until the
   * classifier has run on this frame. Phase 2e (FrameSelector) and Phase 3
   * (FrameScorer) read this.
   */
  protected FrameCategory category;

  /**
   * Position (0-based) of the matching prefix inside the per-category list at
   * classification time. Lower = higher priority (earlier in the allowlist).
   * Set to {@link Integer#MAX_VALUE} when no prefix matched. The bundleId
   * fallback gets a value of {@code inAppPrefixes.size() + 1} so explicit
   * IN_APP rules always outrank it.
   */
  protected int categoryRulePosition = Integer.MAX_VALUE;

  /**
   * Relevance score computed by Phase 3 (FrameScorer). Higher = more
   * informative about the bug. Defaults to 0 until the scorer runs.
   */
  protected double score;

  /**
   * Set by Phase 1c (FrameStripper) when this frame matches a strip pattern.
   * Stripped frames are kept in the {@link ParsedFrames} container (so they
   * stay visible for debugging) but filtered out by the selector.
   */
  protected boolean stripped;
}
