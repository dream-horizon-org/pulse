/**
 * Form Label Constants
 */

export const FORM_LABELS = {
  NAME: "Alert Name",
  NAME_PLACEHOLDER: "e.g., High Error Rate Alert",
  DESCRIPTION: "Description",
  DESCRIPTION_PLACEHOLDER: "Describe what this alert monitors...",
  SCOPE_TYPE: "Scope Type",
  EVALUATION_PERIOD: "Evaluation Period",
  EVALUATION_INTERVAL: "Evaluation Interval",
  METRIC: "Metric",
  OPERATOR: "Operator",
  THRESHOLD: "Threshold",
  EXPRESSION: "Condition Expression",
  SEVERITY: "Alert Severity",
  NOTIFICATION_CHANNEL: "Notification Channel",
} as const;

export const VALIDATION_MESSAGES = {
  NAME_REQUIRED: "Alert name is required",
  NAME_MIN_LENGTH: "Name must be at least 4 characters",
  DESCRIPTION_REQUIRED: "Description is required",
  SCOPE_TYPE_REQUIRED: "Please select a scope type",
  CONDITION_REQUIRED: "Add at least one condition",
  EXPRESSION_REQUIRED: "Expression is required",
  SEVERITY_REQUIRED: "Select severity",
  CHANNEL_REQUIRED: "Select notification channel",
} as const;

export const METRIC_OPERATOR_OPTIONS = [
  { value: "GREATER_THAN", label: "Greater Than (>)" },
  { value: "LESS_THAN", label: "Less Than (<)" },
  { value: "GREATER_THAN_EQUAL", label: "Greater Than or Equal (≥)" },
  { value: "LESS_THAN_EQUAL", label: "Less Than or Equal (≤)" },
] as const;

export const EVALUATION_CONFIG = {
  PERIOD: { MIN: 30, MAX: 3600, DEFAULT: 300, STEP: 30 },
  INTERVAL: { MIN: 30, MAX: 3600, DEFAULT: 60, STEP: 30 },
} as const;

export const UI_CONSTANTS = {
  SCOPE_ITEMS_LIMIT: 10,
  SEARCH_DEBOUNCE_MS: 300,
  MAX_CONDITIONS: 10,
} as const;

