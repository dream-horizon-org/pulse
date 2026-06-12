/**
 * Step Navigation Interfaces
 */

export interface StepNavigationProps {
  onSubmit?: () => void;
  onSubmitError?: (error: Error) => void;
  onCancel?: () => void;
  submitText?: string;
  showCancel?: boolean;
  confirmBeforeSubmit?: boolean;
  confirmTitle?: string;
  confirmMessage?: string;
  className?: string;
}

