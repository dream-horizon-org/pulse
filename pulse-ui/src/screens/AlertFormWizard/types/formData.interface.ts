/**
 * Form Data Interfaces
 */

import { WizardStep } from "./enums";
import {
  NameDescriptionData,
  ScopeTypeData,
  ScopeItemsData,
  GlobalFiltersData,
  EvaluationConfigData,
  ScopeNamesData,
  MetricsConditionsData,
  ConditionExpressionData,
  SeverityNotificationData,
} from "./stepData.interface";

export interface AlertFormWizardData {
  alertId?: number;
  createdBy?: string;
  updatedBy?: string;
  nameDescription: NameDescriptionData;
  scopeType: ScopeTypeData;
  scopeItems: ScopeItemsData;
  globalFilters: GlobalFiltersData;
  evaluationConfig: EvaluationConfigData;
  scopeNames: ScopeNamesData;
  metricsConditions: MetricsConditionsData;
  conditionExpression: ConditionExpressionData;
  severityNotification: SeverityNotificationData;
}

export interface StepConfig {
  id: WizardStep;
  label: string;
  description: string;
  isOptional: boolean;
  isConditional: boolean;
  skipCondition?: (data: AlertFormWizardData) => boolean;
}

export interface StepValidationResult {
  isValid: boolean;
  errors: Record<string, string>;
}

export interface NavigationState {
  currentStep: WizardStep;
  visitedSteps: Set<WizardStep>;
  canGoNext: boolean;
  canGoPrevious: boolean;
  isFirstStep: boolean;
  isLastStep: boolean;
  totalSteps: number;
  effectiveSteps: WizardStep[];
}

