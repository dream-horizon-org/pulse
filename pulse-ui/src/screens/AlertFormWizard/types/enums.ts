/**
 * Alert Form Wizard Enums
 */

export enum AlertScopeType {
  Interaction = "interaction",
  Screen = "screen",
  AppVitals = "app_vitals",
  NetworkAPI = "network_api",
}

export enum MetricOperator {
  GREATER_THAN = "GREATER_THAN",
  LESS_THAN = "LESS_THAN",
  GREATER_THAN_EQUAL = "GREATER_THAN_EQUAL",
  LESS_THAN_EQUAL = "LESS_THAN_EQUAL",
}

export enum WizardStep {
  NAME_DESCRIPTION = 0,
  SELECT_SCOPE = 1,
  METRICS_AND_EXPRESSION = 2,
  GLOBAL_FILTERS = 3,
  EVALUATION_CONFIG = 4,
  SEVERITY_NOTIFICATION = 5,
}

export enum StepValidationState {
  VALID = "valid",
  INVALID = "invalid",
  PENDING = "pending",
}
