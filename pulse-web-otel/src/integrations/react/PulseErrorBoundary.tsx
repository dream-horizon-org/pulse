import React, { Component, type ErrorInfo, type ReactNode } from "react";
import { PulseWeb } from "../../sdk";

export interface PulseErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode | ((error: Error, reset: () => void) => ReactNode);
}

interface State {
  hasError: boolean;
  error: Error | null;
}

/**
 * Catches React render errors and emits `pulse.type = device.crash` via {@link PulseWeb.reportDeviceCrash}.
 * Requires {@link PulseWeb.start} to have completed (`isInitialized()`).
 */
export class PulseErrorBoundary extends Component<
  PulseErrorBoundaryProps,
  State
> {
  constructor(props: PulseErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    PulseWeb.reportDeviceCrash(error, {
      "react.component_stack": info.componentStack ?? "",
    });
  }

  reset = (): void => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError && this.state.error) {
      const { fallback } = this.props;
      if (typeof fallback === "function") {
        return fallback(this.state.error, this.reset);
      }
      if (fallback != null) return fallback;
      return null;
    }
    return this.props.children;
  }
}
