/**
 * Leaf API constants for use in modules that must not import the main Constants barrel
 * (e.g. to avoid circular dependency when used inside CriticalInteractionDetails tree).
 * Do not import from ./Constants or any screens here.
 */

export const API_BASE_URL: string =
  process.env.REACT_APP_PULSE_SERVER_URL ?? "";

export const GET_INTERACTION_ROOT_CAUSE_ROUTE = {
  key: "GET_INTERACTION_ROOT_CAUSE",
  apiPath: "/v1/interactions",
  method: "GET",
} as const;

export const POST_RCA_REPORT_ROUTE = {
  key: "POST_RCA_REPORT",
  apiPath: "/v1/ai/rca/report",
  method: "POST",
} as const;
