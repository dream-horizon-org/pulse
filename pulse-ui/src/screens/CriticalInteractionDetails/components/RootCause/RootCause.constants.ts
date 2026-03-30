export const ROOT_CAUSE_MESSAGES = {
  NO_DATA: "No data available for this interaction in the selected period.",
  FEATURE_OR_NO_DATA:
    "Root cause analysis is not available. The feature may be disabled or there is no data for this period.",
  REQUEST_TIMEOUT:
    "Request timed out. Root cause computation can take up to a minute. Please try again.",
  REQUEST_FAILED: "Failed to load root cause report. Please try again.",
  PROJECT_REQUIRED:
    "Select a project to load root cause analysis for this interaction.",
} as const;
