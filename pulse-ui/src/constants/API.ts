/**
 * Leaf API constants for use in modules that must not import the main Constants barrel
 * (e.g. to avoid circular dependency when used inside CriticalInteractionDetails tree).
 * Do not import from ./Constants or any screens here.
 */

export const HTTP_STATUS = {
  UNAUTHORIZED: 401,
} as const;

export const POST_RCA_REPORT_ROUTE = {
  key: "POST_RCA_REPORT",
  apiPath: "/v1/ai/rca/report",
  method: "POST",
} as const;

export const GET_RCA_JOB_ROUTE = {
  key: "GET_RCA_JOB",
  apiPath: (jobId: string) => `/v1/ai-rca/job/${encodeURIComponent(jobId)}`,
  method: "GET",
} as const;

/** Read-only status check: returns cached report or active job without triggering new job creation. */
export const GET_RCA_STATUS_ROUTE = {
  key: "GET_RCA_STATUS",
  apiPath: (interactionName: string, date?: string | null) => {
    const params = new URLSearchParams({ interactionName });
    if (date) {
      params.set("date", date);
    }
    return `/v1/ai-rca/report?${params.toString()}`;
  },
  method: "GET",
} as const;

/** POST /v1/ai/rca/screen-report — AI executive summary + recommendations for screen RCA (proxied to pulse_ai). */
export const POST_SCREEN_RCA_NARRATIVE_ROUTE = {
  key: "POST_SCREEN_RCA_NARRATIVE",
  apiPath: "/v1/ai/rca/screen-report",
  method: "POST",
} as const;

/**
 * GET /v1/screens/{screen}/root-cause — explicit `?start=&end=` (ISO, end exclusive) or legacy `?date=&asOf=`.
 */
export const GET_SCREEN_ROOT_CAUSE_ROUTE = {
  key: "GET_SCREEN_ROOT_CAUSE",
  apiPathPrefix: "/v1/screens",
  apiPathSuffix: "/root-cause",
  method: "GET",
} as const;
