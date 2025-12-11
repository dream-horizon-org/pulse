/**
 * Alert Form Context Internal Interfaces
 */

import { WizardStep, AlertFormWizardData, StepValidationResult } from "../types";

export type AlertFormAction =
  | { type: "SET_FORM_DATA"; payload: Partial<AlertFormWizardData> }
  | {
      type: "UPDATE_STEP_DATA";
      payload: {
        step: keyof AlertFormWizardData;
        data: Partial<AlertFormWizardData[keyof AlertFormWizardData]>;
      };
    }
  | { type: "GO_TO_STEP"; payload: WizardStep }
  | { type: "GO_TO_NEXT_STEP" }
  | { type: "GO_TO_PREVIOUS_STEP" }
  | { type: "SET_LOADING"; payload: boolean }
  | { type: "SET_DIRTY"; payload: boolean }
  | { type: "RESET_FORM" }
  | { type: "SET_EDIT_MODE"; payload: boolean }
  | { type: "SET_STEP_VALIDATION"; payload: { step: WizardStep; result: StepValidationResult } }
  | { type: "MARK_STEP_VISITED"; payload: WizardStep };

export interface AlertFormState {
  formData: AlertFormWizardData;
  currentStep: WizardStep;
  visitedSteps: Set<WizardStep>;
  isEditMode: boolean;
  isLoading: boolean;
  isDirty: boolean;
  stepValidation: Record<WizardStep, StepValidationResult>;
}

