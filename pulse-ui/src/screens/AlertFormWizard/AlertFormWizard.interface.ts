/**
 * Alert Form Wizard Interfaces
 */

export interface AlertFormWizardProps {
  isInteractionDetailsFlow?: boolean;
  interactionAlertId?: number;
  onBackButtonClick?: () => void;
}

export interface WizardContentProps {
  isInteractionDetailsFlow?: boolean;
  onBackButtonClick?: () => void;
  alertId?: string | null;
}

