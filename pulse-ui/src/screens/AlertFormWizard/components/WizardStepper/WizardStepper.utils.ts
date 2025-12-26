/**
 * Wizard Stepper Utilities
 */

import React from "react";
import { IconCheck, IconX, IconCircle, IconLock } from "@tabler/icons-react";
import { WizardStep } from "../../types";
import { StepState } from "./WizardStepper.interface";

export function getStepState(
  step: WizardStep,
  currentStep: WizardStep,
  isVisited: boolean,
  isValid: boolean,
  isAccessible: boolean
): StepState {
  if (!isAccessible) return "locked";
  if (step === currentStep) return "active";
  if (isVisited && isValid) return "completed";
  if (isVisited && !isValid) return "error";
  return "pending";
}

export function getStateIcon(state: StepState): React.ReactNode {
  switch (state) {
    case "completed":
      return React.createElement(IconCheck, { size: 16, stroke: 2.5 });
    case "error":
      return React.createElement(IconX, { size: 16, stroke: 2.5 });
    case "locked":
      return React.createElement(IconLock, { size: 14 });
    default:
      return React.createElement(IconCircle, { size: 16 });
  }
}

export function getIconSizePixels(size: "sm" | "md" | "lg"): number {
  switch (size) {
    case "sm": return 32;
    case "md": return 40;
    case "lg": return 48;
  }
}

