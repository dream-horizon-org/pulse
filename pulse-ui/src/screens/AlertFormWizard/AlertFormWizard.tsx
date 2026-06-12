/**
 * Alert Form Wizard
 *
 * Main entry point for the alert creation/editing wizard.
 */

import React from "react";
import { useParams } from "react-router-dom";
import { AlertFormProvider } from "./context";
import { WizardContent } from "./components";
import { AlertFormWizardProps } from "./AlertFormWizard.interface";

export const AlertFormWizard: React.FC<AlertFormWizardProps> = ({
  isInteractionDetailsFlow = false,
  interactionAlertId,
  onBackButtonClick,
}) => {
  const params = useParams();
  let alertId = params["*"];

  if (isInteractionDetailsFlow && interactionAlertId) {
    alertId = interactionAlertId.toString();
  }

  const isEditMode = isInteractionDetailsFlow
    ? !!interactionAlertId
    : !!(alertId && alertId !== "*");

  return (
    <AlertFormProvider isEditMode={isEditMode}>
      <WizardContent
        isInteractionDetailsFlow={isInteractionDetailsFlow}
        onBackButtonClick={onBackButtonClick}
        alertId={alertId}
      />
    </AlertFormProvider>
  );
};

export default AlertFormWizard;
