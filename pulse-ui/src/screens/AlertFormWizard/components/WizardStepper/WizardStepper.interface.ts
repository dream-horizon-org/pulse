/**
 * Wizard Stepper Interfaces
 */

import { WizardStep } from "../../types";
import { StepInfo } from "../../hooks";

export interface WizardStepperProps {
  orientation?: "vertical" | "horizontal";
  iconSize?: "sm" | "md" | "lg";
  showNumbers?: boolean;
  clickable?: boolean;
  showProgress?: boolean;
  className?: string;
}

export type StepState = "active" | "completed" | "error" | "pending" | "locked";

export interface StepItemProps {
  step: StepInfo;
  index: number;
  state: StepState;
  isLast: boolean;
  iconSize: "sm" | "md" | "lg";
  showNumber: boolean;
  clickable: boolean;
  orientation: "vertical" | "horizontal";
  onClick: () => void;
}

