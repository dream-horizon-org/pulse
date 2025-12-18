/**
 * Alert Wizard Navigation Hook
 */

import { useCallback, useMemo } from "react";
import { useAlertFormContext } from "../context";
import { WizardStep } from "../types";
import { STEP_CONFIGS } from "./useAlertWizardNavigation.config";
import { StepInfo, NavigationResult, UseAlertWizardNavigationReturn } from "./useAlertWizardNavigation.interface";

export const useAlertWizardNavigation = (): UseAlertWizardNavigationReturn => {
  const { navigation, stepValidation, goToStep: ctxGoToStep, goToNextStep: ctxGoToNextStep, goToPreviousStep: ctxGoToPrevious, validateStep } = useAlertFormContext();
  const { currentStep, visitedSteps, effectiveSteps: stepIds } = navigation;

  const effectiveSteps = useMemo((): StepInfo[] => stepIds.map((id) => ({ id, ...STEP_CONFIGS[id] })), [stepIds]);
  const currentStepIndex = stepIds.indexOf(currentStep);
  const currentStepInfo = useMemo(() => ({ id: currentStep, ...STEP_CONFIGS[currentStep] }), [currentStep]);
  const progressPercentage = Math.round(((currentStepIndex + 1) / stepIds.length) * 100);
  const isCurrentStepValid = stepValidation[currentStep]?.isValid ?? false;

  const isStepVisited = useCallback((step: WizardStep) => visitedSteps.has(step), [visitedSteps]);

  const isStepAccessible = useCallback((step: WizardStep): boolean => {
    const idx = stepIds.indexOf(step);
    if (idx === -1) return false;
    if (idx === 0) return true;
    for (let i = 0; i < idx; i++) if (!visitedSteps.has(stepIds[i])) return false;
    return true;
  }, [stepIds, visitedSteps]);

  const validateCurrentStep = useCallback(() => validateStep(currentStep), [currentStep, validateStep]);

  const goToStep = useCallback((step: WizardStep, skipValidation = false): NavigationResult => {
    if (!isStepAccessible(step)) return { success: false, message: "Complete previous steps first." };
    if (!skipValidation && currentStepIndex < stepIds.indexOf(step)) {
      const result = validateCurrentStep();
      if (!result.isValid) return { success: false, validationErrors: result.errors };
    }
    ctxGoToStep(step);
    return { success: true, targetStep: step };
  }, [isStepAccessible, currentStepIndex, stepIds, validateCurrentStep, ctxGoToStep]);

  const goToNextStep = useCallback((skipValidation = false): NavigationResult => {
    if (!navigation.canGoNext) return { success: false, message: "Already at last step." };
    if (!skipValidation) {
      const result = validateCurrentStep();
      if (!result.isValid) return { success: false, validationErrors: result.errors };
    }
    ctxGoToNextStep();
    return { success: true, targetStep: stepIds[currentStepIndex + 1] };
  }, [navigation.canGoNext, validateCurrentStep, ctxGoToNextStep, stepIds, currentStepIndex]);

  const goToPreviousStep = useCallback((): NavigationResult => {
    if (!navigation.canGoPrevious) return { success: false, message: "Already at first step." };
    ctxGoToPrevious();
    return { success: true, targetStep: stepIds[currentStepIndex - 1] };
  }, [navigation.canGoPrevious, ctxGoToPrevious, stepIds, currentStepIndex]);

  const goToFirstInvalidStep = useCallback((): NavigationResult => {
    for (const id of stepIds) {
      if (!stepValidation[id]?.isValid) { ctxGoToStep(id); return { success: true, targetStep: id, validationErrors: stepValidation[id]?.errors }; }
    }
    return { success: false, message: "All steps valid." };
  }, [stepIds, stepValidation, ctxGoToStep]);

  return {
    currentStep, currentStepInfo, currentStepIndex, totalSteps: stepIds.length, progressPercentage,
    effectiveSteps, visitedSteps, isStepVisited, isStepAccessible,
    canGoNext: navigation.canGoNext, canGoPrevious: navigation.canGoPrevious,
    isFirstStep: navigation.isFirstStep, isLastStep: navigation.isLastStep,
    goToStep, goToNextStep, goToPreviousStep, goToFirstInvalidStep, validateCurrentStep, isCurrentStepValid,
  };
};
