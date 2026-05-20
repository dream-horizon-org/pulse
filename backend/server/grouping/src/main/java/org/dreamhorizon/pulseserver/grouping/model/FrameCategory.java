package org.dreamhorizon.pulseserver.grouping.model;

/**
 * Three-way classification of a stack frame, used by the heuristic pipeline to
 * decide which frames feed the fingerprint.
 *
 * <p>Priority order (highest first): {@link #IN_APP} &gt; {@link #THIRD_PARTY} &gt;
 * {@link #FRAMEWORK}. Phase 2e ("waterfall") selects ALL frames of the highest
 * category present in a trace; if none are {@link #IN_APP} nor
 * {@link #THIRD_PARTY}, it falls back to a small number of {@link #FRAMEWORK}
 * frames and flags the group as a fallback in metrics.</p>
 */
public enum FrameCategory {

  /**
   * The app's own code. These are the frames developers actually own and fix.
   * Source: per-project {@code IN_APP_PACKAGE} rules, with the project's
   * bundleId acting as the lowest-priority fallback.
   */
  IN_APP,

  /**
   * Vendor SDKs and libraries the project explicitly cares about (ad SDKs,
   * payment SDKs, etc.). Used only when there are no {@link #IN_APP} frames.
   * Source: per-project {@code THIRD_PARTY_PACKAGE} rules.
   */
  THIRD_PARTY,

  /**
   * Platform runtimes, language standard libraries, and infrastructure
   * ({@code android.*}, {@code java.*}, {@code kotlin.*}, …). Default category
   * for anything that does not match an {@link #IN_APP} or {@link #THIRD_PARTY}
   * rule; also matched explicitly by {@code FRAMEWORK_PACKAGE} rules so the
   * classifier can distinguish "known framework" from "unclassified default"
   * if needed downstream.
   */
  FRAMEWORK
}
