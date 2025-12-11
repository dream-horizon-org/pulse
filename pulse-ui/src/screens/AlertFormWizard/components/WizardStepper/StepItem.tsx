/**
 * Step Item Component
 */

import React from "react";
import { Box, Text, UnstyledButton, ThemeIcon, Tooltip, Badge } from "@mantine/core";
import { StepItemProps } from "./WizardStepper.interface";
import { STEP_ICONS } from "./WizardStepper.icons";
import { getStateIcon, getIconSizePixels } from "./WizardStepper.utils";
import classes from "./WizardStepper.module.css";

export const StepItem: React.FC<StepItemProps> = ({
  step,
  index,
  state,
  isLast,
  iconSize,
  showNumber,
  clickable,
  orientation,
  onClick,
}) => {
  const IconComponent = STEP_ICONS[step.icon] || STEP_ICONS.IconCircle;
  const sizePixels = getIconSizePixels(iconSize);
  const isClickable = clickable && state !== "locked";

  const getThemeColor = () => {
    if (state === "error") return "red";
    if (state === "locked" || state === "pending") return "gray";
    return "teal";
  };

  const getVariant = (): "filled" | "light" => {
    if (state === "active" || state === "completed") return "filled";
    return "light";
  };

  const content = (
    <Box className={`${classes.stepItem} ${classes[state]}`} data-state={state}>
      <UnstyledButton
        className={`${classes.stepButton} ${isClickable ? classes.clickable : ""}`}
        onClick={isClickable ? onClick : undefined}
        disabled={!isClickable}
        aria-label={`Step ${index + 1}: ${step.label}`}
      >
        <ThemeIcon size={sizePixels} radius="xl" color={getThemeColor()} variant={getVariant()} className={classes.stepIcon}>
          {showNumber ? <Text size="sm" fw={600}>{index + 1}</Text> : state === "active" ? <IconComponent size={sizePixels * 0.45} /> : getStateIcon(state)}
        </ThemeIcon>
        <Box className={classes.stepContent}>
          <Text size="sm" fw={state === "active" ? 600 : 500} className={classes.stepLabel}>{step.label}</Text>
          <Text size="xs" c="dimmed" className={classes.stepDescription}>{step.description}</Text>
        </Box>
        {step.isOptional && <Badge size="xs" variant="light" color="gray">Optional</Badge>}
      </UnstyledButton>
      {!isLast && <Box className={`${classes.connector} ${state === "completed" ? classes.connectorCompleted : ""}`} />}
    </Box>
  );

  if (state === "locked") return <Tooltip label="Complete previous steps" position="right" withArrow>{content}</Tooltip>;
  if (state === "error") return <Tooltip label="Has validation errors" position="right" withArrow color="red">{content}</Tooltip>;
  return content;
};

