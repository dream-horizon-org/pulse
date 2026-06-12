/**
 * Alert Form Context Selector Hooks
 */

import { useAlertFormContext } from "./AlertFormContext";
import { WizardStep, AlertFormWizardData, NavigationState, StepValidationResult } from "../types";

export const useAlertFormData = (): AlertFormWizardData => {
  const { formData } = useAlertFormContext();
  return formData;
};

export const useAlertFormNavigation = (): NavigationState & {
  goToStep: (step: WizardStep) => void;
  goToNextStep: () => void;
  goToPreviousStep: () => void;
} => {
  const { navigation, goToStep, goToNextStep, goToPreviousStep } = useAlertFormContext();
  return { ...navigation, goToStep, goToNextStep, goToPreviousStep };
};

export const useAlertStepValidation = (step: WizardStep): StepValidationResult => {
  const { stepValidation } = useAlertFormContext();
  return stepValidation[step];
};

