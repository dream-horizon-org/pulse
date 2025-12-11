/**
 * Alert Form Context Utilities
 */

import {
  WizardStep,
  AlertScopeType,
  StepValidationResult,
  DEFAULT_WIZARD_FORM_DATA,
} from "../types";
import { AlertFormState } from "./AlertFormContext.interface";

const ALL_STEPS: WizardStep[] = [
  WizardStep.NAME_DESCRIPTION,
  WizardStep.SELECT_SCOPE,
  WizardStep.METRICS_AND_EXPRESSION,
  WizardStep.GLOBAL_FILTERS,
  WizardStep.EVALUATION_CONFIG,
  WizardStep.SEVERITY_NOTIFICATION,
];

export const getEffectiveSteps = (_scopeType: AlertScopeType | null): WizardStep[] => {
  return ALL_STEPS;
};

export const createInitialValidation = (): Record<WizardStep, StepValidationResult> => ({
  [WizardStep.NAME_DESCRIPTION]: { isValid: false, errors: {} },
  [WizardStep.SELECT_SCOPE]: { isValid: false, errors: {} },
  [WizardStep.METRICS_AND_EXPRESSION]: { isValid: false, errors: {} },
  [WizardStep.GLOBAL_FILTERS]: { isValid: true, errors: {} },
  [WizardStep.EVALUATION_CONFIG]: { isValid: true, errors: {} },
  [WizardStep.SEVERITY_NOTIFICATION]: { isValid: false, errors: {} },
});

export const createInitialState = (): AlertFormState => ({
  formData: { ...DEFAULT_WIZARD_FORM_DATA },
  currentStep: WizardStep.NAME_DESCRIPTION,
  visitedSteps: new Set([WizardStep.NAME_DESCRIPTION]),
  isEditMode: false,
  isLoading: false,
  isDirty: false,
  stepValidation: createInitialValidation(),
});
