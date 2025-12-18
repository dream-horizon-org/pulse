/**
 * Step Content Placeholder Component
 */

import React from "react";
import { Box, Title, Text } from "@mantine/core";
import { useAlertWizardNavigation } from "../../hooks";
import classes from "../../AlertFormWizard.module.css";

export const StepContentPlaceholder: React.FC = () => {
  const { currentStep, currentStepInfo } = useAlertWizardNavigation();

  return (
    <Box className={classes.stepContent}>
      <Box className={classes.stepHeader}>
        <Title order={3} className={classes.stepTitle}>
          {currentStepInfo.label}
        </Title>
        <Text size="sm" c="dimmed">
          {currentStepInfo.description}
        </Text>
      </Box>
      <Box className={classes.stepBody}>
        <Text c="dimmed" ta="center" py="xl">
          Step {currentStep + 1} content will be implemented in Phase 2.
        </Text>
        <Text size="sm" c="dimmed" ta="center">
          Current step: {currentStepInfo.label}
        </Text>
      </Box>
    </Box>
  );
};

