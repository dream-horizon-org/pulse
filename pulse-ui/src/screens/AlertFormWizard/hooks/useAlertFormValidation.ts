/**
 * Alert Form Validation Hook
 */

import { useCallback, useMemo } from "react";
import { useAlertFormContext } from "../context";
import { WizardStep, AlertScopeType, MetricCondition } from "../types";
import { FieldValidationResult, UseAlertFormValidationReturn } from "./useAlertFormValidation.interface";
import {
  NAME_RULES,
  DESCRIPTION_RULES,
  EVAL_PERIOD_RULES,
  EVAL_INTERVAL_RULES,
  runRules,
  validateExpressionSyntax,
  validateThresholdValue,
} from "./useAlertFormValidation.rules";

export const useAlertFormValidation = (): UseAlertFormValidationReturn => {
  const { formData, stepValidation, validateStep: ctxValidateStep, validateAllSteps: ctxValidateAll } =
    useAlertFormContext();

  const validateName = useCallback((n: string) => runRules(n, NAME_RULES, formData), [formData]);
  const validateDescription = useCallback((d: string) => runRules(d, DESCRIPTION_RULES, formData), [formData]);
  const validateScopeType = useCallback(
    (s: AlertScopeType | null): FieldValidationResult =>
      s ? { isValid: true } : { isValid: false, error: "Select a scope type" },
    []
  );
  const validateEvaluationPeriod = useCallback((p: number) => runRules(p, EVAL_PERIOD_RULES, formData), [formData]);
  const validateEvaluationInterval = useCallback(
    (i: number) => runRules(i, EVAL_INTERVAL_RULES, formData),
    [formData]
  );
  const validateConditionExpression = useCallback(
    (e: string) => validateExpressionSyntax(e, formData.metricsConditions.conditions.map((c) => c.alias)),
    [formData.metricsConditions.conditions]
  );

  const validateMetricCondition = useCallback(
    (c: MetricCondition, i: number): Record<string, string> => {
      const errs: Record<string, string> = {};
      if (!c.metric) errs[`condition_${i}_metric`] = "Metric required";
      // Threshold must have at least one scope_name -> value entry
      if (!c.threshold || Object.keys(c.threshold).length === 0) {
        errs[`condition_${i}_threshold`] = "Add at least one scope name with threshold";
      } else {
        // Validate that all threshold values are non-negative
        for (const [scopeName, value] of Object.entries(c.threshold)) {
          const thresholdValidation = validateThresholdValue(value, scopeName);
          if (!thresholdValidation.isValid) {
            errs[`condition_${i}_threshold_${scopeName}`] = thresholdValidation.error || "Invalid threshold";
          }
        }
      }
      return errs;
    },
    []
  );

  const validateField = useCallback(
    (_step: keyof typeof formData, field: string, value: unknown): FieldValidationResult => {
      switch (field) {
        case "name": return validateName(value as string);
        case "description": return validateDescription(value as string);
        case "scopeType": return validateScopeType(value as AlertScopeType | null);
        case "evaluationPeriod": return validateEvaluationPeriod(value as number);
        case "evaluationInterval": return validateEvaluationInterval(value as number);
        case "expression": return validateConditionExpression(value as string);
        default: return { isValid: true };
      }
    },
    [validateName, validateDescription, validateScopeType, validateEvaluationPeriod, validateEvaluationInterval, validateConditionExpression]
  );

  const isFormValid = useMemo(() => Object.values(stepValidation).every((v) => v.isValid), [stepValidation]);

  const getFirstInvalidStep = useCallback((): WizardStep | null => {
    const steps = Object.keys(stepValidation).map(Number) as WizardStep[];
    return steps.find((s) => !stepValidation[s]?.isValid) ?? null;
  }, [stepValidation]);

  const getValidationSummary = useCallback(() => {
    const steps = Object.keys(stepValidation).map(Number) as WizardStep[];
    const valid = steps.filter((s) => stepValidation[s]?.isValid);
    const invalid = steps.filter((s) => !stepValidation[s]?.isValid);
    const total = invalid.reduce((acc, s) => acc + Object.keys(stepValidation[s]?.errors || {}).length, 0);
    return { validSteps: valid, invalidSteps: invalid, totalErrors: total };
  }, [stepValidation]);

  return {
    validateStep: ctxValidateStep,
    validateAllSteps: ctxValidateAll,
    getStepValidation: (s) => stepValidation[s] || { isValid: false, errors: {} },
    isStepValid: (s) => stepValidation[s]?.isValid ?? false,
    validateField,
    validateName,
    validateDescription,
    validateScopeType,
    validateEvaluationPeriod,
    validateEvaluationInterval,
    validateConditionExpression,
    validateMetricCondition,
    isFormValid,
    getFirstInvalidStep,
    getValidationSummary,
  };
};
