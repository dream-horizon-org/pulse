/**
 * Step Configuration
 */

import { WizardStep } from "../types";
import { StepInfo } from "./useAlertWizardNavigation.interface";

export const STEP_CONFIGS: Record<WizardStep, Omit<StepInfo, "id">> = {
  [WizardStep.NAME_DESCRIPTION]: {
    label: "Name & Description",
    description: "Identify your alert",
    icon: "IconPencil",
    isOptional: false,
  },
  [WizardStep.SELECT_SCOPE]: {
    label: "Select Scope",
    description: "Choose what to monitor",
    icon: "IconTarget",
    isOptional: false,
  },
  [WizardStep.METRICS_AND_EXPRESSION]: {
    label: "Conditions",
    description: "Metrics, thresholds & expression",
    icon: "IconChartBar",
    isOptional: false,
  },
  [WizardStep.GLOBAL_FILTERS]: {
    label: "Global Filters",
    description: "Filter by dimensions",
    icon: "IconFilter",
    isOptional: true,
  },
  [WizardStep.EVALUATION_CONFIG]: {
    label: "Evaluation",
    description: "Set timing parameters",
    icon: "IconClock",
    isOptional: false,
  },
  [WizardStep.SEVERITY_NOTIFICATION]: {
    label: "Severity & Notification",
    description: "Configure alerts",
    icon: "IconBell",
    isOptional: false,
  },
};
