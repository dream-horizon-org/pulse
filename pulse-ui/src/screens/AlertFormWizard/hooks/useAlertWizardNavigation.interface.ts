/**
 * Navigation Hook Interfaces
 */

import { WizardStep, StepValidationResult } from "../types";

export interface StepInfo {
  id: WizardStep;
  label: string;
  description: string;
  icon: string;
  isOptional: boolean;
}

export interface NavigationResult {
  success: boolean;
  targetStep?: WizardStep;
  validationErrors?: Record<string, string>;
  message?: string;
}

export interface UseAlertWizardNavigationReturn {
  currentStep: WizardStep;
  currentStepInfo: StepInfo;
  currentStepIndex: number;
  totalSteps: number;
  progressPercentage: number;
  effectiveSteps: StepInfo[];
  visitedSteps: Set<WizardStep>;
  isStepVisited: (step: WizardStep) => boolean;
  isStepAccessible: (step: WizardStep) => boolean;
  canGoNext: boolean;
  canGoPrevious: boolean;
  isFirstStep: boolean;
  isLastStep: boolean;
  goToStep: (step: WizardStep, skipValidation?: boolean) => NavigationResult;
  goToNextStep: (skipValidation?: boolean) => NavigationResult;
  goToPreviousStep: () => NavigationResult;
  goToFirstInvalidStep: () => NavigationResult;
  validateCurrentStep: () => StepValidationResult;
  isCurrentStepValid: boolean;
}

