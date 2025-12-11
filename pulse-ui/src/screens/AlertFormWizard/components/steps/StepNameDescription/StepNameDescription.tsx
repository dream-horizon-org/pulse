/**
 * Step 1: Name & Description
 */

import React, { useCallback } from "react";
import { Box, TextInput, Textarea } from "@mantine/core";
import { useAlertFormContext } from "../../../context";
import { useAlertFormValidation } from "../../../hooks";
import { FORM_LABELS } from "../../../constants";
import { StepHeader } from "../StepHeader";
import { StepNameDescriptionProps } from "./StepNameDescription.interface";
import classes from "./StepNameDescription.module.css";

export const StepNameDescription: React.FC<StepNameDescriptionProps> = ({ className }) => {
  const { formData, updateStepData } = useAlertFormContext();
  const { validateName, validateDescription } = useAlertFormValidation();
  const { name, description } = formData.nameDescription;

  const handleNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    updateStepData("nameDescription", { name: e.target.value });
  }, [updateStepData]);

  const handleDescriptionChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    updateStepData("nameDescription", { description: e.target.value });
  }, [updateStepData]);

  const nameValidation = name ? validateName(name) : { isValid: true };
  const descValidation = description ? validateDescription(description) : { isValid: true };

  return (
    <Box className={`${classes.container} ${className || ""}`}>
      <StepHeader title="Name & Description" description="Give your alert a clear name and description" />

      <Box className={classes.form}>
        <TextInput
          label={FORM_LABELS.NAME}
          placeholder={FORM_LABELS.NAME_PLACEHOLDER}
          description="A unique, descriptive name (4-100 characters)"
          value={name}
          onChange={handleNameChange}
          error={!nameValidation.isValid ? nameValidation.error : undefined}
          required
          classNames={{ input: classes.input, label: classes.label }}
        />

        <Textarea
          label={FORM_LABELS.DESCRIPTION}
          placeholder={FORM_LABELS.DESCRIPTION_PLACEHOLDER}
          description="Describe what this alert monitors (10-500 characters)"
          value={description}
          onChange={handleDescriptionChange}
          error={!descValidation.isValid ? descValidation.error : undefined}
          minRows={4}
          required
          classNames={{ input: classes.input, label: classes.label }}
        />
      </Box>
    </Box>
  );
};
