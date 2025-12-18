/**
 * Alert Form Context Reducer
 */

import { AlertFormAction, AlertFormState } from "./AlertFormContext.interface";
import { getEffectiveSteps, createInitialState } from "./AlertFormContext.utils";

export const alertFormReducer = (
  state: AlertFormState,
  action: AlertFormAction
): AlertFormState => {
  switch (action.type) {
    case "SET_FORM_DATA":
      return { ...state, formData: { ...state.formData, ...action.payload }, isDirty: true };

    case "UPDATE_STEP_DATA": {
      const { step, data } = action.payload;
      const currentStepData = state.formData[step];
      const updatedStepData =
        typeof currentStepData === "object" && currentStepData !== null
          ? Object.assign({}, currentStepData, data)
          : data;
      return {
        ...state,
        formData: { ...state.formData, [step]: updatedStepData },
        isDirty: true,
      };
    }

    case "GO_TO_STEP": {
      const effectiveSteps = getEffectiveSteps(state.formData.scopeType.scopeType);
      if (!effectiveSteps.includes(action.payload)) return state;
      return {
        ...state,
        currentStep: action.payload,
        visitedSteps: new Set(Array.from(state.visitedSteps).concat(action.payload)),
      };
    }

    case "GO_TO_NEXT_STEP": {
      const effectiveSteps = getEffectiveSteps(state.formData.scopeType.scopeType);
      const currentIndex = effectiveSteps.indexOf(state.currentStep);
      if (currentIndex >= effectiveSteps.length - 1) return state;
      const nextStep = effectiveSteps[currentIndex + 1];
      return {
        ...state,
        currentStep: nextStep,
        visitedSteps: new Set(Array.from(state.visitedSteps).concat(nextStep)),
      };
    }

    case "GO_TO_PREVIOUS_STEP": {
      const effectiveSteps = getEffectiveSteps(state.formData.scopeType.scopeType);
      const currentIndex = effectiveSteps.indexOf(state.currentStep);
      if (currentIndex <= 0) return state;
      return { ...state, currentStep: effectiveSteps[currentIndex - 1] };
    }

    case "SET_LOADING":
      return { ...state, isLoading: action.payload };

    case "SET_DIRTY":
      return { ...state, isDirty: action.payload };

    case "RESET_FORM":
      return createInitialState();

    case "SET_EDIT_MODE":
      return { ...state, isEditMode: action.payload };

    case "SET_STEP_VALIDATION":
      return {
        ...state,
        stepValidation: { ...state.stepValidation, [action.payload.step]: action.payload.result },
      };

    case "MARK_STEP_VISITED":
      return {
        ...state,
        visitedSteps: new Set(Array.from(state.visitedSteps).concat(action.payload)),
      };

    default:
      return state;
  }
};

