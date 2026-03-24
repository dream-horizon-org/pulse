export const ROOT_CAUSE_MESSAGES = {
  GENERIC_ERROR: "Something went wrong.",
  NO_DATA: "No data available for this interaction in the selected period.",
  FEATURE_OR_NO_DATA:
    "Root cause analysis is not available. The feature may be disabled or there is no data for this period.",
  REQUEST_TIMEOUT:
    "Request timed out. Root cause computation can take up to a minute. Please try again.",
} as const;
