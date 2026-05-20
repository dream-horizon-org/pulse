package org.dreamhorizon.pulseserver.grouping.model;

import java.util.List;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;

/**
 * Immutable bundle of per-project grouping rules consumed by the heuristic
 * pipeline. Every list is pre-validated and (for pattern fields) pre-compiled
 * so the hot path runs without any extra parsing work.
 *
 * <p>Construction is intentionally done <strong>outside</strong> the grouping
 * module — the server-side loader reads DB rows + the bundleId, compiles the
 * regexes once, and hands the resulting {@code GroupingRules} to
 * {@link org.dreamhorizon.pulseserver.grouping.Grouper#group}. Tests use
 * {@link #builder()} directly. {@link #empty()} returns a no-op instance that
 * leaves every frame unclassified (i.e. {@code FRAMEWORK}) and applies no
 * masking or stripping — handy for the legacy single-arg {@code Grouper.group}
 * entry point.</p>
 */
@Getter
@Builder
@ToString
public final class GroupingRules {

  /**
   * Ordered list of package prefixes that mark a frame as
   * {@link FrameCategory#IN_APP}. Position 0 is highest priority — it wins
   * scoring ties within IN_APP.
   */
  @NonNull
  @Singular
  private final List<String> inAppPrefixes;

  /**
   * Ordered list of package prefixes that mark a frame as
   * {@link FrameCategory#THIRD_PARTY}. Position 0 is highest priority.
   */
  @NonNull
  @Singular
  private final List<String> thirdPartyPrefixes;

  /**
   * Ordered list of package prefixes that explicitly mark a frame as
   * {@link FrameCategory#FRAMEWORK}. Anything not matching IN_APP or
   * THIRD_PARTY already defaults to FRAMEWORK — these prefixes are useful when
   * a rule needs to <em>override</em> a broader IN_APP/THIRD_PARTY match (the
   * classifier checks IN_APP first, so listing exception subpackages here will
   * only matter if you also intentionally leave them out of higher tiers).
   */
  @NonNull
  @Singular
  private final List<String> frameworkPrefixes;

  /**
   * Pre-compiled patterns that tag a frame as "stripped" — frames whose
   * {@code token} matches any of these are flagged via
   * {@link Frame#setStripped(boolean)} and skipped during frame selection.
   * Tag-don't-delete so they remain visible for debugging.
   */
  @NonNull
  @Singular
  private final List<Pattern> stripPatterns;

  /**
   * Ordered mask rules applied to frame tokens (and to the exception message
   * for Phase 4). Order matters when multiple rules overlap.
   */
  @NonNull
  @Singular
  private final List<MaskRule> maskRules;

  /**
   * Project bundleId used as a final-fallback IN_APP prefix when no DB rule
   * matched. Nullable. When set, any frame whose package starts with this
   * value is classified as {@link FrameCategory#IN_APP} but at the
   * <strong>lowest</strong> priority within IN_APP (so explicit rules always
   * win the position tie-break).
   */
  private final String bundleIdFallback;

  /**
   * Returns an empty instance with no prefixes, no strip patterns, no mask
   * rules, and no bundleId fallback. Every frame stays {@code FRAMEWORK} and
   * the masker / stripper become no-ops.
   */
  public static GroupingRules empty() {
    return GroupingRules.builder().build();
  }
}
