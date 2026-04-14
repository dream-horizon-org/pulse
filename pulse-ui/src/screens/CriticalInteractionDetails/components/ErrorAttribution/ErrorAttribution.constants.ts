export const ERROR_ATTRIBUTION_MESSAGES = {
  SECTION_TITLE: "Error attribution",
  REFRESH: "Refresh attribution",
  NO_DATA_IN_WINDOW: "No interaction data in this window",
  INSUFFICIENT_POOR:
    "Insufficient data — fewer than 1,000 Poor sessions in this window",
  GENERIC_ERROR: "Something went wrong while loading error attribution.",
} as const;

/** U+2013 — use for undefined p1/p2/RR (not hyphen-minus). */
export const EN_DASH = "\u2013";
