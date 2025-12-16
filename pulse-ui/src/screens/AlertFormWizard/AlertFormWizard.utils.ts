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
  interaction: "interaction",
  network_api: "network_api",
  screen: "screen",
  app_vitals: "app_vitals",
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
  console.log("[transformAlertDetailsToFormData] Input:", alertDetails);

  // Map conditions from API to form format
  const conditions: MetricCondition[] = (alertDetails.alerts || []).map((alert: AlertCondition, index: number) => ({
    id: `cond_${Date.now()}_${index}`,
    alias: alert.alias || String.fromCharCode(65 + index),
    metric: alert.metric || "",
    operator: (alert.metric_operator as MetricOperator) || MetricOperator.GREATER_THAN,
    threshold: alert.threshold || {},
  }));

  // Extract unique scope names from all conditions' thresholds
  const scopeNamesSet = new Set<string>();
  conditions.forEach(cond => {
    Object.keys(cond.threshold || {}).forEach(scopeName => scopeNamesSet.add(scopeName));
  });
  const selectedScopeNames = Array.from(scopeNamesSet);

  console.log("[transformAlertDetailsToFormData] Conditions:", conditions);
  console.log("[transformAlertDetailsToFormData] Scope names:", selectedScopeNames);

  // Map backend AlertScope enum to frontend (handle various casing)
  // @see backend/server/.../models/AlertScope.java
  const backendToFrontend: Record<string, AlertScopeType> = {
    Interaction: AlertScopeType.Interaction,
    INTERACTION: AlertScopeType.Interaction,
    interaction: AlertScopeType.Interaction,
    API: AlertScopeType.NetworkAPI,
    api: AlertScopeType.NetworkAPI,
    network_api: AlertScopeType.NetworkAPI,
    SCREEN: AlertScopeType.Screen,
    Screen: AlertScopeType.Screen,
    screen: AlertScopeType.Screen,
    APP_VITALS: AlertScopeType.AppVitals,
    app_vitals: AlertScopeType.AppVitals,
    AppVitals: AlertScopeType.AppVitals,
  };

  const mappedScope = backendToFrontend[alertDetails.scope] || null;
  console.log("[transformAlertDetailsToFormData] Mapped scope:", alertDetails.scope, "->", mappedScope);

  const result: Partial<AlertFormWizardData> = {
    alertId: alertDetails.alert_id,
    nameDescription: {
      name: alertDetails.name || "",
      description: alertDetails.description || "",
    },
    scopeType: {
      scopeType: mappedScope,
    },
    evaluationConfig: {
      evaluationPeriod: alertDetails.evaluation_period || 300,
      evaluationInterval: alertDetails.evaluation_interval || 60,
    },
    severityNotification: {
      severityId: alertDetails.severity_id || null,
      notificationChannelId: alertDetails.notification_channel_id || null,
    },
    metricsConditions: {
      selectedScopeNames,
      conditions: conditions.length > 0 ? conditions : [{
        id: `cond_${Date.now()}`,
        alias: "A",
        metric: "",
        operator: MetricOperator.GREATER_THAN,
        threshold: {},
      }],
    },
    conditionExpression: {
      expression: alertDetails.condition_expression || "A",
    },
  };

  console.log("[transformAlertDetailsToFormData] Result:", result);
  return result;
}
