/**
 * API Interfaces
 * 
 * Backend expected payload structure:
 * - scope: "INTERACTIONS" | "API" | "SCREEN" | "APP_VITALS"
 * - dimension_filters: SQL-like expression for app version, OS version filters
 * - threshold: Map of scope_name -> threshold_value (e.g., { "checkout_flow": 0.8 })
 */

import { MetricOperator } from "./enums";

export interface AlertApiPayload {
  alert_id?: number;
  name: string;
  description: string;
  scope: string;
  dimension_filters?: string;
  condition_expression: string;
  alerts: Array<{
    alias: string;
    metric: string;
    metric_operator: MetricOperator;
    /**
     * Threshold is a map of scope_name -> threshold_value
     * Each scope name gets its own threshold, operator is common
     */
    threshold: Record<string, number>;
  }>;
  evaluation_period: number;
  evaluation_interval: number;
  severity_id: number;
  notification_channel_id: number;
  created_by: string;
  updated_by?: string;
}

