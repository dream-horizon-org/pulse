/**
 * Shared Step Header Component
 * Provides consistent title + description + divider for all steps
 */

import React from "react";
import { Box, Text, Divider } from "@mantine/core";
import classes from "./shared.module.css";

interface StepHeaderProps {
  title: string;
  description: string;
}

export const StepHeader: React.FC<StepHeaderProps> = ({ title, description }) => (
  <Box className={classes.stepHeader}>
    <Text className={classes.stepTitle}>{title}</Text>
    <Text className={classes.stepDescription}>{description}</Text>
    <Divider className={classes.stepDivider} />
  </Box>
);

