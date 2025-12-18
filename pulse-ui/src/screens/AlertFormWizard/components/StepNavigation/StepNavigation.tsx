/**
 * Step Navigation Component
 */

import React, { useCallback, useState } from "react";
import { Box, Button, Group, Text, Tooltip } from "@mantine/core";
import { IconArrowLeft, IconArrowRight, IconCheck, IconAlertCircle } from "@tabler/icons-react";
import { useAlertWizardNavigation } from "../../hooks";
import { useAlertFormContext } from "../../context";
import { StepNavigationProps } from "./StepNavigation.interface";
import { ConfirmModal } from "./ConfirmModal";
import classes from "./StepNavigation.module.css";

export const StepNavigation: React.FC<StepNavigationProps> = ({
  onSubmit,
  onSubmitError,
  onCancel,
  submitText = "Create Alert",
  showCancel = true,
  confirmBeforeSubmit = true,
  confirmTitle = "Confirm Alert Creation",
  confirmMessage = "Are you sure you want to create this alert?",
  className,
}) => {
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const { canGoNext, canGoPrevious, isFirstStep, isLastStep, goToNextStep, goToPreviousStep, goToFirstInvalidStep, validateCurrentStep, isCurrentStepValid } = useAlertWizardNavigation();
  const { isEditMode, isLoading, submitForm, validateAllSteps } = useAlertFormContext();

  const handleNext = useCallback(() => {
    setValidationError(null);
    const result = validateCurrentStep();
    if (!result.isValid) { setValidationError(Object.values(result.errors)[0] || "Fix errors"); return; }
    goToNextStep(true);
  }, [validateCurrentStep, goToNextStep]);

  const handlePrevious = useCallback(() => { setValidationError(null); goToPreviousStep(); }, [goToPreviousStep]);

  const handleConfirmSubmit = useCallback(async () => {
    setShowConfirmModal(false);
    setIsSubmitting(true);
    try { await submitForm(); onSubmit?.(); }
    catch (e) { const err = e instanceof Error ? e : new Error("Failed"); setValidationError(err.message); onSubmitError?.(err); }
    finally { setIsSubmitting(false); }
  }, [submitForm, onSubmit, onSubmitError]);

  const handleSubmitClick = useCallback(() => {
    setValidationError(null);
    if (!validateAllSteps()) { goToFirstInvalidStep(); setValidationError("Complete all fields"); return; }
    confirmBeforeSubmit ? setShowConfirmModal(true) : handleConfirmSubmit();
  }, [validateAllSteps, goToFirstInvalidStep, confirmBeforeSubmit, handleConfirmSubmit]);

  const isDisabled = isLoading || isSubmitting;

  return (
    <Box className={`${classes.navigation} ${className || ""}`}>
      {validationError && <Box className={classes.errorAlert}><IconAlertCircle size={16} /><Text size="sm">{validationError}</Text></Box>}
      <Group className={classes.buttonGroup} justify="space-between">
        <Box>{showCancel && <Button variant="subtle" color="gray" onClick={onCancel} disabled={isDisabled}>Cancel</Button>}</Box>
        <Group gap="md">
          {!isFirstStep && <Button variant="default" leftSection={<IconArrowLeft size={18} />} onClick={handlePrevious} disabled={!canGoPrevious || isDisabled}>Previous</Button>}
          {isLastStep ? (
            <Tooltip label={!isCurrentStepValid ? "Complete this step" : "Submit"} disabled={isCurrentStepValid}>
              <Button variant="filled" color="teal" rightSection={<IconCheck size={18} />} onClick={handleSubmitClick} disabled={isDisabled} loading={isSubmitting}>{isEditMode ? "Update Alert" : submitText}</Button>
            </Tooltip>
          ) : (
            <Button variant="outline" color="teal" rightSection={<IconArrowRight size={18} />} onClick={handleNext} disabled={!canGoNext || isDisabled}>Next</Button>
          )}
        </Group>
      </Group>
      <ConfirmModal opened={showConfirmModal} onClose={() => setShowConfirmModal(false)} onConfirm={handleConfirmSubmit} title={confirmTitle} message={confirmMessage} isLoading={isSubmitting} isEditMode={isEditMode} />
    </Box>
  );
};
