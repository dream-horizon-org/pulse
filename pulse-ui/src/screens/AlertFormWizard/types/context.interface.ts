/**
 * Context Interfaces
 */

import { WizardStep } from "./enums";
import { AlertFormWizardData, NavigationState, StepValidationResult } from "./formData.interface";

export interface AlertFormContextValue {
  formData: AlertFormWizardData;
  isEditMode: boolean;
  isLoading: boolean;
  isDirty: boolean;
  navigation: NavigationState;
  stepValidation: Record<WizardStep, StepValidationResult>;

  updateStepData: <K extends keyof AlertFormWizardData>(
    step: K,
    data: Partial<AlertFormWizardData[K]>
  ) => void;
  setFormData: (data: Partial<AlertFormWizardData>) => void;
  goToStep: (step: WizardStep) => void;
  goToNextStep: () => void;
  goToPreviousStep: () => void;
  validateStep: (step: WizardStep) => StepValidationResult;
  validateAllSteps: () => boolean;
  resetForm: () => void;
  submitForm: () => Promise<void>;
}

export interface AlertFormProviderProps {
  children: React.ReactNode;
  initialData?: Partial<AlertFormWizardData>;
  isEditMode?: boolean;
  onSubmit?: (data: AlertFormWizardData) => Promise<void>;
}

