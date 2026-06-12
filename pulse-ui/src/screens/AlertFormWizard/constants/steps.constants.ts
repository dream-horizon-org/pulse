/**
 * Step Configuration Constants
 */

import { WizardStep, StepConfig } from "../types";

export const WIZARD_STEP_CONFIGS: StepConfig[] = [
  { id: WizardStep.NAME_DESCRIPTION, label: "Name & Description", description: "Identify your alert", isOptional: false, isConditional: false },
  { id: WizardStep.SELECT_SCOPE, label: "Select Scope", description: "Choose entity type", isOptional: false, isConditional: false },
  { id: WizardStep.METRICS_AND_EXPRESSION, label: "Conditions", description: "Define alert conditions", isOptional: false, isConditional: false },
  { id: WizardStep.GLOBAL_FILTERS, label: "Global Filters", description: "Filter by dimensions", isOptional: true, isConditional: false },
  { id: WizardStep.EVALUATION_CONFIG, label: "Evaluation Settings", description: "Configure timing", isOptional: false, isConditional: false },
  { id: WizardStep.SEVERITY_NOTIFICATION, label: "Severity & Notification", description: "Configure alerts", isOptional: false, isConditional: false },
];
