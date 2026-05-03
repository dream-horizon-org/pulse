export const ERROR_ATTRIBUTION_MESSAGES = {
  SECTION_TITLE: "Error attribution",
  NO_DATA_IN_WINDOW: "No interaction data in this window",
  GENERIC_ERROR: "Something went wrong while loading error attribution.",
  DRILL_DOWN_TOGGLE_SHOW: "Top issues (sessions in U)",
  DRILL_DOWN_TOGGLE_HIDE: "Hide top issues",
  DRILL_DOWN_LOADING: "Loading drill-down…",
  DRILL_DOWN_ERROR: "Could not load drill-down for this signal.",
  DRILL_DOWN_EMPTY:
    "No issues met the minimum session thresholds in this window, or none ranked in the top list.",
  RELATED_LIST_EMPTY:
    "No issues met the minimum session and ranking rules in this window.",
  DRILL_DOWN_SESSIONS: "Sessions in U",
} as const;

export function relatedAttributionsEmptyMessage(
  minRiskRatioForIssueAttribution?: number | null,
): string {
  if (
    minRiskRatioForIssueAttribution != null &&
    minRiskRatioForIssueAttribution > 1
  ) {
    return `No issues met the related threshold (RR ≥ ${minRiskRatioForIssueAttribution}) in this window.`;
  }
  return ERROR_ATTRIBUTION_MESSAGES.RELATED_LIST_EMPTY;
}

/** U+2013 — use for undefined p1/p2/RR (not hyphen-minus). */
export const EN_DASH = "\u2013";
