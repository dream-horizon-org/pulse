/** Interval for GET /v1/ai-rca/job/{jobId} while job is in flight. */
export const RCA_JOB_POLL_MS = 3000 as const;

/** Background POST to detect cache changes or async activity while viewing a completed report. */
export const RCA_STALE_CACHE_POLL_MS = 30_000 as const;

/** RCA report types supported by the async job system. */
export const RCA_TYPE = {
  INTERACTION: "INTERACTION",
  SESSION: "SESSION",
  SCREEN: "SCREEN",
} as const;

export const ROOT_CAUSE_MESSAGES = {
  GENERIC_ERROR: "Something went wrong.",
  REGENERATE_REPORT: "Regenerate report",
  NO_DATA: "No data available for this interaction in the selected period.",
  /** Per-segment placeholder when rank/title exist but body has nothing to show. */
  RCA_SEGMENT_NO_DETAIL:
    "No metrics or supporting evidence for this segment in this report.",
  FEATURE_OR_NO_DATA:
    "Root cause analysis is not available. The feature may be disabled or there is no data for this period.",
  REQUEST_TIMEOUT:
    "Request timed out. Root cause computation can take up to a minute. Please try again.",
  RCA_WAITING_IN_QUEUE: "Generating your report, it may take a few minutes…",
  RCA_JOINING_JOB:
    "Another user is generating this report. Joining existing job.",
  RCA_STALE_REPORT_BANNER:
    "Report has been regenerated. Refresh to see updates.",
  RCA_STALE_ASYNC_ACTIVITY:
    "New report activity was detected (a generation may be in progress). Refresh to follow progress or load the latest report.",
  RCA_STALE_REFRESH: "Refresh",
  RCA_UNKNOWN_JOB_STATUS:
    "Received an unexpected job status from the server. Try refreshing or retrying.",
  RCA_COMPLETED_INVALID_REPORT:
    "The report finished generating but the response could not be displayed. You can retry to generate again.",
} as const;

/** Hover help for RCA segment metrics table column headers. */
export const RCA_METRICS_COLUMN_TOOLTIPS = {
  METRIC:
    "Name of the measure (for example error rate, Apdex, or duration). Each row is one metric for this segment.",
  VALUE:
    "How this segment performs on the metric inside the analysis window—the cohort Pulse used for this RCA report.",
  BASELINE:
    "Reference level for the same metric, usually from a broader cohort than the segment. It is the comparison anchor, not a separate time range label.",
  DELTA:
    "Difference between this segment’s value and the baseline. Formatting follows the report (for example higher error rate may read as worse).",
} as const;
