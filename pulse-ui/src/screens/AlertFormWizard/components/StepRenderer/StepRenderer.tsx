/**
 * Step Renderer Component
 */

import React from "react";
import { Box, Text } from "@mantine/core";
import { useAlertWizardNavigation } from "../../hooks";
import { WizardStep } from "../../types";
import { StepNameDescription, StepSelectScope, StepMetricsAndExpression, StepGlobalFilters, StepEvaluationConfig, StepSeverityNotification } from "../steps";
import classes from "./StepRenderer.module.css";

const STEP_COMPONENTS: Record<WizardStep, React.FC> = {
  [WizardStep.NAME_DESCRIPTION]: StepNameDescription,
  [WizardStep.SELECT_SCOPE]: StepSelectScope,
  [WizardStep.METRICS_AND_EXPRESSION]: StepMetricsAndExpression,
  [WizardStep.GLOBAL_FILTERS]: StepGlobalFilters,
  [WizardStep.EVALUATION_CONFIG]: StepEvaluationConfig,
  [WizardStep.SEVERITY_NOTIFICATION]: StepSeverityNotification,
};

export const StepRenderer: React.FC = () => {
  const { currentStep } = useAlertWizardNavigation();
  const StepComponent = STEP_COMPONENTS[currentStep];

  if (!StepComponent) {
    return (
      <Box className={classes.container}>
        <Text c="dimmed">Unknown step</Text>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      <StepComponent />
    </Box>
  );
};
