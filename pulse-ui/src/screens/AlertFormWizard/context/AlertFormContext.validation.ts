/**
 * Alert Form Step Validation Logic
 */

import {
  WizardStep,
  AlertFormWizardData,
  StepValidationResult,
} from "../types";

export const validateStepData = (
  step: WizardStep,
  formData: AlertFormWizardData
): StepValidationResult => {
  const errors: Record<string, string> = {};

  switch (step) {
    case WizardStep.NAME_DESCRIPTION: {
      const { name, description } = formData.nameDescription;
      if (!name || name.trim().length < 4) errors.name = "Name must be at least 4 characters";
      if (!description || description.trim().length < 10)
        errors.description = "Description must be at least 10 characters";
      break;
    }

    case WizardStep.SELECT_SCOPE:
      if (!formData.scopeType.scopeType) errors.scopeType = "Please select a scope type";
      break;

    case WizardStep.METRICS_AND_EXPRESSION: {
      const { conditions, selectedScopeNames } = formData.metricsConditions;
      const isAppVitals = formData.scopeType.scopeType === "app_vitals";
      
      // Validate scope names (not required for App Vitals)
      if (!isAppVitals && (!selectedScopeNames || selectedScopeNames.length === 0)) {
        errors.scopeNames = "Select at least one scope name to monitor";
      }
      
      // Validate conditions
      if (conditions.length === 0) errors.conditions = "Add at least one condition";
      conditions.forEach((c, i) => {
        if (!c.metric) errors[`condition_${i}_metric`] = "Metric required";
      });
      
      // Validate expression
      const { expression } = formData.conditionExpression;
      if (!expression?.trim()) errors.expression = "Expression required";
      break;
    }

    case WizardStep.EVALUATION_CONFIG: {
      const { evaluationPeriod, evaluationInterval } = formData.evaluationConfig;
      if (evaluationPeriod < 30 || evaluationPeriod > 3600)
        errors.evaluationPeriod = "Period must be 30-3600 seconds";
      if (evaluationInterval < 30 || evaluationInterval > 3600)
        errors.evaluationInterval = "Interval must be 30-3600 seconds";
      if (evaluationInterval > evaluationPeriod)
        errors.evaluationInterval = "Interval cannot exceed period";
      break;
    }

    case WizardStep.SEVERITY_NOTIFICATION: {
      const { severityId, notificationChannelId } = formData.severityNotification;
      if (severityId === null) errors.severityId = "Select severity";
      if (notificationChannelId === null) errors.notificationChannelId = "Select channel";
      break;
    }

    // GLOBAL_FILTERS is optional - always valid
    case WizardStep.GLOBAL_FILTERS:
      break;
  }

  return { isValid: Object.keys(errors).length === 0, errors };
};
