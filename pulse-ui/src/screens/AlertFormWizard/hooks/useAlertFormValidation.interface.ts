/**
 * Validation Hook Interfaces
 */

import { WizardStep, AlertFormWizardData, StepValidationResult, MetricCondition, AlertScopeType } from "../types";

export interface ValidationRule<T> {
  validate: (value: T, formData: AlertFormWizardData) => boolean;
  message: string;
}

export interface FieldValidationResult {
  isValid: boolean;
  error?: string;
}

export interface UseAlertFormValidationReturn {
  validateStep: (step: WizardStep) => StepValidationResult;
  validateAllSteps: () => boolean;
  getStepValidation: (step: WizardStep) => StepValidationResult;
  isStepValid: (step: WizardStep) => boolean;
  validateField: <K extends keyof AlertFormWizardData>(
    step: K,
    field: string,
    value: unknown
  ) => FieldValidationResult;
  validateName: (name: string) => FieldValidationResult;
  validateDescription: (description: string) => FieldValidationResult;
  validateScopeType: (scopeType: AlertScopeType | null) => FieldValidationResult;
  validateEvaluationPeriod: (period: number) => FieldValidationResult;
  validateEvaluationInterval: (interval: number) => FieldValidationResult;
  validateConditionExpression: (expression: string) => FieldValidationResult;
  validateMetricCondition: (condition: MetricCondition, index: number) => Record<string, string>;
  isFormValid: boolean;
  getFirstInvalidStep: () => WizardStep | null;
  getValidationSummary: () => { validSteps: WizardStep[]; invalidSteps: WizardStep[]; totalErrors: number };
}

