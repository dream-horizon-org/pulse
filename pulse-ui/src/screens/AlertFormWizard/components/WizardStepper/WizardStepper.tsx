/**
 * Wizard Stepper Component
 */

import React, { useCallback, useMemo } from "react";
import { Box, Text, Progress } from "@mantine/core";
import { useAlertWizardNavigation } from "../../hooks";
import { useAlertFormContext } from "../../context";
import { WizardStep } from "../../types";
import { WizardStepperProps } from "./WizardStepper.interface";
import { getStepState } from "./WizardStepper.utils";
import { StepItem } from "./StepItem";
import classes from "./WizardStepper.module.css";

export const WizardStepper: React.FC<WizardStepperProps> = ({
  orientation = "vertical",
  iconSize = "md",
  showNumbers = false,
  clickable = true,
  showProgress = false,
  className,
}) => {
  const { currentStep, effectiveSteps, progressPercentage, isStepVisited, isStepAccessible, goToStep } =
    useAlertWizardNavigation();
  const { stepValidation } = useAlertFormContext();

  const handleStepClick = useCallback((step: WizardStep) => goToStep(step, true), [goToStep]);

  const stepStates = useMemo(
    () =>
      effectiveSteps.map((step) =>
        getStepState(step.id, currentStep, isStepVisited(step.id), stepValidation[step.id]?.isValid ?? false, isStepAccessible(step.id))
      ),
    [effectiveSteps, currentStep, isStepVisited, isStepAccessible, stepValidation]
  );

  return (
    <Box className={`${classes.stepper} ${className || ""}`} role="navigation" aria-label="Form progress">
      {showProgress && (
        <Box className={classes.progressContainer}>
          <Text size="xs" c="dimmed" mb={4}>Progress: {progressPercentage}%</Text>
          <Progress value={progressPercentage} size="sm" color="teal" radius="xl" />
        </Box>
      )}
      <Box className={classes.stepsContainer} role="list">
        {effectiveSteps.map((step, index) => (
          <StepItem
            key={step.id}
            step={step}
            index={index}
            state={stepStates[index]}
            isLast={index === effectiveSteps.length - 1}
            iconSize={iconSize}
            showNumber={showNumbers}
            clickable={clickable}
            orientation={orientation}
            onClick={() => handleStepClick(step.id)}
          />
        ))}
      </Box>
    </Box>
  );
};
