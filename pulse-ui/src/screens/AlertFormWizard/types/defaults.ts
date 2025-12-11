/**
 * Default Values
 */

import {
  NameDescriptionData,
  ScopeTypeData,
  ScopeItemsData,
  GlobalFiltersData,
  EvaluationConfigData,
  ScopeNamesData,
  MetricsConditionsData,
  ConditionExpressionData,
  SeverityNotificationData,
  MetricCondition,
} from "./stepData.interface";
import { AlertFormWizardData } from "./formData.interface";
import { MetricOperator } from "./enums";

export const DEFAULT_NAME_DESCRIPTION: NameDescriptionData = {
  name: "",
  description: "",
};

export const DEFAULT_SCOPE_TYPE: ScopeTypeData = {
  scopeType: null,
};

export const DEFAULT_SCOPE_ITEMS: ScopeItemsData = {
  availableScopeItems: [],
  selectedScopeItems: [],
};

export const DEFAULT_GLOBAL_FILTERS: GlobalFiltersData = {
  filterBuilder: {
    groups: [{
      id: "group_default",
      conditions: [{
        id: "cond_default",
        field: "",
        operator: "=",
        value: "",
      }],
      logicalOperator: "AND",
    }],
    groupOperator: "AND",
  },
};

export const DEFAULT_EVALUATION_CONFIG: EvaluationConfigData = {
  evaluationPeriod: 300,
  evaluationInterval: 60,
};

export const DEFAULT_SCOPE_NAMES: ScopeNamesData = {
  selectedScopeNames: [],
};

// Default first condition - always present
const DEFAULT_FIRST_CONDITION: MetricCondition = {
  id: "cond_default",
  alias: "A",
  metric: "",
  operator: MetricOperator.GREATER_THAN,
  threshold: {},
};

export const DEFAULT_METRICS_CONDITIONS: MetricsConditionsData = {
  selectedScopeNames: [],
  conditions: [DEFAULT_FIRST_CONDITION],
};

export const DEFAULT_CONDITION_EXPRESSION: ConditionExpressionData = {
  expression: "A",
};

export const DEFAULT_SEVERITY_NOTIFICATION: SeverityNotificationData = {
  severityId: null,
  notificationChannelId: null,
};

export const DEFAULT_WIZARD_FORM_DATA: AlertFormWizardData = {
  nameDescription: DEFAULT_NAME_DESCRIPTION,
  scopeType: DEFAULT_SCOPE_TYPE,
  scopeItems: DEFAULT_SCOPE_ITEMS,
  globalFilters: DEFAULT_GLOBAL_FILTERS,
  evaluationConfig: DEFAULT_EVALUATION_CONFIG,
  scopeNames: DEFAULT_SCOPE_NAMES,
  metricsConditions: DEFAULT_METRICS_CONDITIONS,
  conditionExpression: DEFAULT_CONDITION_EXPRESSION,
  severityNotification: DEFAULT_SEVERITY_NOTIFICATION,
};

