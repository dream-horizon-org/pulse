/**
 * Alert Form Context Provider
 */
import React, { createContext, useCallback, useContext, useMemo, useReducer, useRef } from "react";
import { WizardStep, AlertFormWizardData, NavigationState, StepValidationResult, AlertFormContextValue, AlertFormProviderProps } from "../types";
import { alertFormReducer } from "./AlertFormContext.reducer";
import { createInitialState, getEffectiveSteps } from "./AlertFormContext.utils";
import { validateStepData } from "./AlertFormContext.validation";

export const AlertFormContext = createContext<AlertFormContextValue | null>(null);

export const AlertFormProvider: React.FC<AlertFormProviderProps> = ({ children, initialData, isEditMode = false, onSubmit }) => {
  const [state, dispatch] = useReducer(alertFormReducer, null, () => {
    const initial = createInitialState();
    return initialData ? { ...initial, formData: { ...initial.formData, ...initialData }, isEditMode } : { ...initial, isEditMode };
  });

  const onSubmitRef = useRef(onSubmit);
  onSubmitRef.current = onSubmit;

  const validateStep = useCallback((step: WizardStep): StepValidationResult => {
    const result = validateStepData(step, state.formData);
    dispatch({ type: "SET_STEP_VALIDATION", payload: { step, result } });
    return result;
  }, [state.formData]);

  const validateAllSteps = useCallback((): boolean => {
    return getEffectiveSteps(state.formData.scopeType.scopeType).every((s) => validateStep(s).isValid);
  }, [state.formData.scopeType.scopeType, validateStep]);

  const updateStepData = useCallback(<K extends keyof AlertFormWizardData>(step: K, data: Partial<AlertFormWizardData[K]>) => {
    dispatch({ type: "UPDATE_STEP_DATA", payload: { step, data: data as never } });
  }, []);

  const navigation = useMemo((): NavigationState => {
    const steps = getEffectiveSteps(state.formData.scopeType.scopeType);
    const idx = steps.indexOf(state.currentStep);
    return { currentStep: state.currentStep, visitedSteps: state.visitedSteps, canGoNext: idx < steps.length - 1, canGoPrevious: idx > 0, isFirstStep: idx === 0, isLastStep: idx === steps.length - 1, totalSteps: steps.length, effectiveSteps: steps };
  }, [state.currentStep, state.visitedSteps, state.formData.scopeType.scopeType]);

  const submitForm = useCallback(async () => {
    dispatch({ type: "SET_LOADING", payload: true });
    try {
      if (!validateAllSteps()) throw new Error("Validation failed");
      if (onSubmitRef.current) await onSubmitRef.current(state.formData);
      dispatch({ type: "SET_DIRTY", payload: false });
    } finally { dispatch({ type: "SET_LOADING", payload: false }); }
  }, [state.formData, validateAllSteps]);

  const value = useMemo((): AlertFormContextValue => ({
    formData: state.formData, isEditMode: state.isEditMode, isLoading: state.isLoading, isDirty: state.isDirty,
    navigation, stepValidation: state.stepValidation, updateStepData,
    setFormData: (data) => dispatch({ type: "SET_FORM_DATA", payload: data }),
    goToStep: (step) => dispatch({ type: "GO_TO_STEP", payload: step }),
    goToNextStep: () => dispatch({ type: "GO_TO_NEXT_STEP" }),
    goToPreviousStep: () => dispatch({ type: "GO_TO_PREVIOUS_STEP" }),
    validateStep, validateAllSteps, resetForm: () => dispatch({ type: "RESET_FORM" }), submitForm,
  }), [state, navigation, updateStepData, validateStep, validateAllSteps, submitForm]);

  return <AlertFormContext.Provider value={value}>{children}</AlertFormContext.Provider>;
};

export const useAlertFormContext = (): AlertFormContextValue => {
  const ctx = useContext(AlertFormContext);
  if (!ctx) throw new Error("useAlertFormContext must be within AlertFormProvider");
  return ctx;
};
