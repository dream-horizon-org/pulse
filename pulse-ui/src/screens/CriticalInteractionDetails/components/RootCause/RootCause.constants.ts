/** Interval for GET /v1/ai-rca/job/{jobId} while job is in flight. */
export const RCA_JOB_POLL_MS = 3000 as const;

/** Background POST to detect cache changes or async activity while viewing a completed report. */
export const RCA_STALE_CACHE_POLL_MS = 30_000 as const;

export const ROOT_CAUSE_MESSAGES = {
  GENERIC_ERROR: "Something went wrong.",
  REGENERATE_REPORT: "Regenerate report",
  NO_DATA: "No data available for this interaction in the selected period.",
  FEATURE_OR_NO_DATA:
    "Root cause analysis is not available. The feature may be disabled or there is no data for this period.",
  REQUEST_TIMEOUT:
    "Request timed out. Root cause computation can take up to a minute. Please try again.",
  RCA_WAITING_IN_QUEUE: "Generating your report…",
  RCA_JOINING_JOB: "Another user is generating this report. Joining existing job.",
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
