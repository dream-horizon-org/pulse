package org.dreamhorizon.pulseserver.grouping.model;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A single masking rule: a pre-compiled regex paired with the replacement that
 * should be substituted in for every match. Used by Phase 1b to normalize
 * volatile substrings (line numbers, hex addresses, UUIDs, Metro bundle URLs,
 * etc.) before frames are hashed into a fingerprint.
 *
 * <p>Instances are immutable and pre-compile their pattern at construction
 * time. Build them once at rule-load time and reuse for every event.</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class MaskRule {

  private final Pattern regex;
  private final String replacement;

  /**
   * Static factory that compiles {@code regex} once. Prefer this over
   * constructing a {@link Pattern} at every call site.
   */
  public static MaskRule of(String regex, String replacement) {
    if (regex == null) {
      throw new IllegalArgumentException("regex must not be null");
    }
    return new MaskRule(Pattern.compile(regex), replacement == null ? "" : replacement);
  }

  /**
   * Apply this rule to {@code input}, replacing every match with the configured
   * replacement. Returns {@code input} unchanged if it is {@code null}.
   */
  public String apply(String input) {
    if (input == null) {
      return null;
    }
    return regex.matcher(input).replaceAll(replacement);
  }
}
