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

/**
 * Read-only peek: GET /v1/ai-rca/report?rcaType=&entityKey=&date=
 * Matches backend {@code GetRcaJobStatus.peekRcaStatus}; does not create jobs.
 */
export const GET_RCA_STATUS_ROUTE = {
  key: "GET_RCA_STATUS",
  apiPath: (entityKey: string, rcaType: string, date?: string | null) => {
    const params = new URLSearchParams({
      rcaType,
      entityKey,
    });
    if (date) {
      params.set("date", date);
    }
    return `/v1/ai-rca/report?${params.toString()}`;
  },
  method: "GET",
} as const;

/** POST /v1/ai/rca/screen-report — AI narrative for screen RCA (proxied to pulse_ai). */
export const POST_SCREEN_RCA_NARRATIVE_ROUTE = {
  key: "POST_SCREEN_RCA_NARRATIVE",
  apiPath: "/v1/ai/rca/screen-report",
  method: "POST",
} as const;

/** GET /v1/screens/{screen}/root-cause — `date` + `asOf` (same as interaction RCA). */
export const GET_SCREEN_ROOT_CAUSE_ROUTE = {
  key: "GET_SCREEN_ROOT_CAUSE",
  apiPathPrefix: "/v1/screens",
  apiPathSuffix: "/root-cause",
  method: "GET",
} as const;
