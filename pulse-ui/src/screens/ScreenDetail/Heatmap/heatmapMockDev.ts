/**
 * Dev helpers when `REACT_APP_USE_MOCK_SERVER=true` — scenario toolbar maps UI
 * screen picks to magic API `screenName` values the mock understands.
 */

export type HeatmapMockProfile =
  | "live"
  | "empty"
  | "error"
  | "no_screenshots"
  | "sparse"
  /** Second fixture seed so compare column B looks clearly different from A (mock only). */
  | "alternate";

export function isHeatmapMockServerEnabled(): boolean {
  return process.env.REACT_APP_USE_MOCK_SERVER === "true";
}

export const HEATMAP_MOCK_PROFILE_LABELS: Record<HeatmapMockProfile, string> = {
  live: "Use real screen name (default)",
  empty: "Empty — no bins",
  error: "API error",
  no_screenshots: "No screenshots (heatmap only)",
  sparse: "Sparse bins",
  alternate: "Alternate dense layout (compare B)",
};

/** Profiles listed for the primary / Screen A query. */
export const HEATMAP_MOCK_PROFILES_PRIMARY: HeatmapMockProfile[] = [
  "live",
  "empty",
  "error",
  "no_screenshots",
  "sparse",
];

/** Screen B additionally gets “alternate” for side‑by‑side contrast. */
export const HEATMAP_MOCK_PROFILES_COMPARE_B: HeatmapMockProfile[] = [
  ...HEATMAP_MOCK_PROFILES_PRIMARY,
  "alternate",
];

export function mockProfileToApiScreenName(
  profile: HeatmapMockProfile,
  uiScreenName: string,
): string {
  switch (profile) {
    case "live":
      return uiScreenName;
    case "empty":
      return "__empty__";
    case "error":
      return "__error__";
    case "no_screenshots":
      return "__no_screenshots__";
    case "sparse":
      return "__sparse__";
    case "alternate":
      return "__mock_compare_b__";
    default:
      return uiScreenName;
  }
}
