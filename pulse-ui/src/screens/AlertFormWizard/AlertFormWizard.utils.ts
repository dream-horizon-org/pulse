/**
 * Alert Form Wizard Utilities
 */

import { AlertFormWizardData, AlertApiPayload, AlertScopeType, MetricOperator, MetricCondition } from "./types";
import { AlertListItem, AlertCondition } from "../../hooks/useGetAlertList/useGetAlertList.interface";

/**
 * Map frontend scope types to backend AlertScope enum values
 * @see backend/server/.../models/AlertScope.java
 */
const SCOPE_TYPE_TO_BACKEND: Record<string, string> = {
  interaction: "Interaction",
  network_api: "API",
  screen: "SCREEN",
  app_vitals: "APP_VITALS",
};

/**
 * Build dimension_filters expression from FilterBuilder data
 * Supports complex expressions like:
 * (`AppVersion` = '1.0.1' AND `Platform` = 'ios') OR (`AppVersion` = '1.0.2' AND `Platform` = 'android')
 */
function buildDimensionFilters(globalFilters: AlertFormWizardData["globalFilters"]): string | undefined {
  const { filterBuilder } = globalFilters;
  
  const groupExpressions = filterBuilder.groups
    .map((group) => {
      const conditions = group.conditions
        .filter((c) => c.field && c.value)
        .map((c) => {
          const val = c.operator === "LIKE" ? `'%${c.value}%'` : `'${c.value}'`;
          const op = c.operator === "LIKE" ? "LIKE" : c.operator;
          return `\`${c.field}\` ${op} ${val}`;
        });
      if (conditions.length === 0) return "";
      if (conditions.length === 1) return conditions[0];
      return `(${conditions.join(` ${group.logicalOperator.toLowerCase()} `)})`;
    })
    .filter(Boolean);

  if (groupExpressions.length === 0) return undefined;
  if (groupExpressions.length === 1) return groupExpressions[0];
  return groupExpressions.join(` ${filterBuilder.groupOperator.toLowerCase()} `);
}

export function transformFormDataToPayload(
  formData: AlertFormWizardData,
  userEmail: string
): AlertApiPayload {
  const {
    nameDescription,
    scopeType,
    evaluationConfig,
    severityNotification,
    metricsConditions,
    conditionExpression,
    globalFilters,
  } = formData;

  // Map frontend scope to backend enum
  const backendScope = SCOPE_TYPE_TO_BACKEND[scopeType.scopeType || ""] || scopeType.scopeType || "";

  // Transform conditions - threshold is already a map of scope_name -> value
  const alerts = metricsConditions.conditions.map((condition) => ({
    alias: condition.alias,
    metric: condition.metric,
    metric_operator: condition.operator,
    threshold: condition.threshold,
  }));

  // Build dimension filters expression
  const dimensionFilters = buildDimensionFilters(globalFilters);

  return {
    alert_id: formData.alertId,
    name: nameDescription.name,
    description: nameDescription.description,
    scope: backendScope,
    dimension_filters: dimensionFilters,
    condition_expression: conditionExpression.expression,
    alerts,
    evaluation_period: evaluationConfig.evaluationPeriod,
    evaluation_interval: evaluationConfig.evaluationInterval,
    severity_id: severityNotification.severityId || 1,
    notification_channel_id: severityNotification.notificationChannelId || 1,
    created_by: userEmail,
    updated_by: userEmail,
  };
}

/**
 * Transform alert details from API to form data
 */
export function transformAlertDetailsToFormData(alertDetails: AlertListItem): Partial<AlertFormWizardData> {
  const conditions: MetricCondition[] = alertDetails.alerts.map((alert: AlertCondition, index: number) => ({
    id: `cond_${index}`,
    alias: alert.alias,
    metric: alert.metric,
    operator: alert.metric_operator as MetricOperator,
    threshold: alert.threshold,
  }));

  // Extract unique scope names from all conditions' thresholds
  const scopeNamesSet = new Set<string>();
  conditions.forEach(cond => {
    Object.keys(cond.threshold).forEach(scopeName => scopeNamesSet.add(scopeName));
  });
  const selectedScopeNames = Array.from(scopeNamesSet);

  // Map backend AlertScope enum to frontend
  // @see backend/server/.../models/AlertScope.java
  const backendToFrontend: Record<string, AlertScopeType> = {
    Interaction: AlertScopeType.Interaction,
    API: AlertScopeType.NetworkAPI,
    SCREEN: AlertScopeType.Screen,
    APP_VITALS: AlertScopeType.AppVitals,
  };

  return {
    alertId: alertDetails.alert_id,
    nameDescription: {
      name: alertDetails.name,
      description: alertDetails.description || "",
    },
    scopeType: {
      scopeType: backendToFrontend[alertDetails.scope] || null,
    },
    evaluationConfig: {
      evaluationPeriod: alertDetails.evaluation_period,
      evaluationInterval: alertDetails.evaluation_interval,
    },
    severityNotification: {
      severityId: alertDetails.severity_id,
      notificationChannelId: alertDetails.notification_channel_id,
    },
    metricsConditions: {
      selectedScopeNames,
      conditions,
    },
    conditionExpression: {
      expression: alertDetails.condition_expression,
    },
  };
}
