/**
 * Step 5: Evaluation Configuration
 */

import React, { useCallback } from "react";
import { Box, Text, NumberInput, Slider, Group } from "@mantine/core";
import { useAlertFormContext } from "../../../context";
import { useAlertFormValidation } from "../../../hooks";
import { EVALUATION_CONFIG } from "../../../constants";
import { StepHeader } from "../StepHeader";
import classes from "./StepEvaluationConfig.module.css";

export interface StepEvaluationConfigProps { className?: string; }

export const StepEvaluationConfig: React.FC<StepEvaluationConfigProps> = ({ className }) => {
  const { formData, updateStepData } = useAlertFormContext();
  const { validateEvaluationPeriod, validateEvaluationInterval } = useAlertFormValidation();
  const { evaluationPeriod, evaluationInterval } = formData.evaluationConfig;

  const handlePeriodChange = useCallback((val: number | string) => {
    updateStepData("evaluationConfig", { evaluationPeriod: Number(val) || EVALUATION_CONFIG.PERIOD.DEFAULT });
  }, [updateStepData]);

  const handleIntervalChange = useCallback((val: number | string) => {
    updateStepData("evaluationConfig", { evaluationInterval: Number(val) || EVALUATION_CONFIG.INTERVAL.DEFAULT });
  }, [updateStepData]);

  const periodValidation = validateEvaluationPeriod(evaluationPeriod);
  const intervalValidation = validateEvaluationInterval(evaluationInterval);

  return (
    <Box className={`${classes.container} ${className || ""}`}>
      <StepHeader title="Evaluation Settings" description="Configure how often the alert condition is evaluated" />

      <Box className={classes.form}>
        <Box className={classes.field}>
          <Text className={classes.fieldLabel}>Evaluation Period (seconds)</Text>
          <Text className={classes.fieldDesc}>Time window for evaluating the alert condition</Text>
          <Group align="center" gap="md">
            <Slider value={evaluationPeriod} onChange={handlePeriodChange} min={EVALUATION_CONFIG.PERIOD.MIN} max={EVALUATION_CONFIG.PERIOD.MAX} step={EVALUATION_CONFIG.PERIOD.STEP} style={{ flex: 1 }} color="teal" />
            <NumberInput value={evaluationPeriod} onChange={handlePeriodChange} min={EVALUATION_CONFIG.PERIOD.MIN} max={EVALUATION_CONFIG.PERIOD.MAX} step={EVALUATION_CONFIG.PERIOD.STEP} w={100} error={!periodValidation.isValid ? periodValidation.error : undefined} />
          </Group>
        </Box>

        <Box className={classes.field}>
          <Text className={classes.fieldLabel}>Evaluation Interval (seconds)</Text>
          <Text className={classes.fieldDesc}>How often to check the alert condition</Text>
          <Group align="center" gap="md">
            <Slider value={evaluationInterval} onChange={handleIntervalChange} min={EVALUATION_CONFIG.INTERVAL.MIN} max={Math.min(evaluationPeriod, EVALUATION_CONFIG.INTERVAL.MAX)} step={EVALUATION_CONFIG.INTERVAL.STEP} style={{ flex: 1 }} color="teal" />
            <NumberInput value={evaluationInterval} onChange={handleIntervalChange} min={EVALUATION_CONFIG.INTERVAL.MIN} max={EVALUATION_CONFIG.INTERVAL.MAX} step={EVALUATION_CONFIG.INTERVAL.STEP} w={100} error={!intervalValidation.isValid ? intervalValidation.error : undefined} />
          </Group>
        </Box>
      </Box>
    </Box>
  );
};
