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
