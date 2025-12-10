/**
 * Step Data Interfaces
 */

import { MetricOperator } from "./enums";

export interface NameDescriptionData {
  name: string;
  description: string;
}

export interface ScopeTypeData {
  scopeType: import("./enums").AlertScopeType | null;
}

export interface ScopeItem {
  id: string;
  name: string;
  displayLabel: string;
  metadata?: Record<string, unknown>;
}

export interface ScopeItemsData {
  availableScopeItems: ScopeItem[];
  selectedScopeItems: ScopeItem[];
}

export type FilterOperator = "=" | "!=" | "LIKE";
export type LogicalOperator = "AND" | "OR";

export interface FilterCondition {
  id: string;
  field: string;
  operator: FilterOperator;
  value: string;
}

export interface FilterGroup {
  id: string;
  conditions: FilterCondition[];
  logicalOperator: LogicalOperator;
}

export interface FilterBuilderData {
  groups: FilterGroup[];
  groupOperator: LogicalOperator;
}

export interface GlobalFiltersData {
  filterBuilder: FilterBuilderData;
}

export interface EvaluationConfigData {
  evaluationPeriod: number;
  evaluationInterval: number;
}

export interface ScopeNameSelection {
  id: string;
  name: string;
  displayLabel: string;
}

export interface ScopeNamesData {
  selectedScopeNames: ScopeNameSelection[];
}

export interface MetricCondition {
  id: string;
  alias: string;
  metric: string;
  operator: MetricOperator;
  /**
   * Threshold is a map of scope_name -> threshold_value
   * e.g., { "checkout_flow": 0.8, "payment_process": 0.9 }
   * Each scope name can have its own threshold, but metric & operator are common
   */
  threshold: Record<string, number>;
}

export interface MetricsConditionsData {
  /** Global scope names that apply to ALL conditions */
  selectedScopeNames: string[];
  conditions: MetricCondition[];
}

export interface ConditionExpressionData {
  expression: string;
}

export interface SeverityNotificationData {
  severityId: number | null;
  notificationChannelId: number | null;
}

