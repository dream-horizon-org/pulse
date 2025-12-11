/**
 * Wizard Content Component
 */

import React, { useCallback, useEffect, useState, useRef } from "react";
import { Box, LoadingOverlay, useMantineTheme } from "@mantine/core";
import { useNavigate } from "react-router-dom";
import { IconCircleCheckFilled, IconSquareRoundedX } from "@tabler/icons-react";
import { useAlertFormContext } from "../../context";
import { WizardStepper, StepNavigation, WizardHeader, DeleteModal, StepRenderer } from "../index";
import { WizardContentProps } from "../../AlertFormWizard.interface";
import { transformFormDataToPayload, transformAlertDetailsToFormData } from "../../AlertFormWizard.utils";
import { showNotification } from "../../../../helpers/showNotification";
import { useGetAlertDetails } from "../../../../hooks/useGetAlertDetails";
import { useCreateAlert } from "../../../../hooks/useCreateAlert";
import { useUpdateAlert } from "../../../../hooks/useUpdateAlert";
import { useAlertDelete } from "../../../../hooks/useDeleteAlert/useDeleteAlert";
import { getCookies } from "../../../../helpers/cookies";
import { COMMON_CONSTANTS, COOKIES_KEY, ROUTES } from "../../../../constants";
import { MODAL_TEXTS } from "../../constants";
import classes from "../../AlertFormWizard.module.css";

export const WizardContent: React.FC<WizardContentProps> = ({ isInteractionDetailsFlow = false, onBackButtonClick, alertId }) => {
  const theme = useMantineTheme();
  const navigate = useNavigate();
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showLoader, setShowLoader] = useState(false);
  const { formData, isEditMode, isLoading, setFormData } = useAlertFormContext();
  
  // Track if we've already populated the form to prevent infinite loops
  const hasPopulatedForm = useRef(false);

  const navigateBack = useCallback(() => {
    setTimeout(() => isInteractionDetailsFlow ? onBackButtonClick?.() : navigate(ROUTES["ALERTS"].basePath), 1500);
  }, [isInteractionDetailsFlow, onBackButtonClick, navigate]);

  const showSuccess = useCallback((msg: string) => {
    setShowLoader(false);
    showNotification(COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE, msg, <IconCircleCheckFilled />, theme.colors.teal[6]);
    navigateBack();
  }, [theme, navigateBack]);

  const showError = useCallback((msg: string) => {
    setShowLoader(false);
    showNotification(COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE, msg, <IconSquareRoundedX />, theme.colors.red[6]);
  }, [theme]);

  const { data: alertDetailsResponse, isLoading: isLoadingDetails, error } = useGetAlertDetails({ queryParams: { alert_id: alertId || null } });

  const createMutation = useCreateAlert((response) => {
    if (response?.error) { showError(response.error.message); return; }
    showSuccess("Alert created successfully.");
  });

  const updateMutation = useUpdateAlert((response) => {
    if (response?.error) { showError(response.error.message); return; }
    showSuccess("Alert updated successfully.");
  });

  const deleteMutation = useAlertDelete((response) => {
    if (response?.error) { showError(response.error.message); return; }
    showSuccess("Alert deleted successfully.");
  }, alertId || null);

  // Handle loading and errors
  useEffect(() => {
    if (error) showError(error.message);
    setShowLoader(isLoadingDetails);
  }, [error, isLoadingDetails, showError]);

  // Populate form with alert details when editing (only once)
  useEffect(() => {
    if (alertDetailsResponse?.data && isEditMode && !hasPopulatedForm.current) {
      hasPopulatedForm.current = true;
      const formDataFromDetails = transformAlertDetailsToFormData(alertDetailsResponse.data);
      setFormData(formDataFromDetails);
    }
  }, [alertDetailsResponse, isEditMode, setFormData]);

  const handleClose = useCallback(() => isInteractionDetailsFlow ? onBackButtonClick?.() : navigate(ROUTES["ALERTS"].basePath), [isInteractionDetailsFlow, onBackButtonClick, navigate]);

  const handleSubmit = useCallback(async () => {
    const email = getCookies(COOKIES_KEY.USER_EMAIL);
    if (!email) { showError(COMMON_CONSTANTS.USER_EMAIL_NOT_FOUND); return; }
    setShowLoader(true);
    const payload = transformFormDataToPayload(formData, email);
    isEditMode ? updateMutation.mutateAsync(payload as never) : createMutation.mutateAsync(payload as never);
  }, [formData, isEditMode, createMutation, updateMutation, showError]);

  const handleDeleteConfirm = useCallback(() => { setShowDeleteModal(false); setShowLoader(true); deleteMutation.mutateAsync(null); }, [deleteMutation]);

  return (
    <Box className={classes.wizardContainer}>
      <LoadingOverlay visible={showLoader || isLoading} loaderProps={{ type: "bars" }} zIndex={1000} />
      <WizardHeader isUpdateFlow={isEditMode} onDelete={() => setShowDeleteModal(true)} onClose={handleClose} />
      <Box className={classes.wizardContent}>
        <Box className={classes.stepperSidebar}><WizardStepper /></Box>
        <Box className={classes.stepContentArea}><StepRenderer /></Box>
      </Box>
      <StepNavigation onSubmit={handleSubmit} onCancel={handleClose} submitText={isEditMode ? "Update Alert" : "Create Alert"} confirmTitle={isEditMode ? MODAL_TEXTS.UPDATE_TITLE : MODAL_TEXTS.CREATE_TITLE} confirmMessage={isEditMode ? MODAL_TEXTS.UPDATE_MESSAGE : MODAL_TEXTS.CREATE_MESSAGE} />
      <DeleteModal opened={showDeleteModal} onClose={() => setShowDeleteModal(false)} onConfirm={handleDeleteConfirm} />
    </Box>
  );
};
