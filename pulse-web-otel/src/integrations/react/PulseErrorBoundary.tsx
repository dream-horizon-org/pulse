import React, { Component, type ErrorInfo, type ReactNode } from "react";
import { Pulse } from "../../sdk";
import type { PulseErrorBoundaryProps } from "../../types/react";

export type { PulseErrorBoundaryProps } from "../../types/react";

interface State {
  hasError: boolean;
  error: Error | null;
}

/**
 * Catches React render errors and emits `pulse.type = device.crash` via {@link Pulse.reportDeviceCrash}.
 * Requires {@link Pulse.init} to have completed (`isInitialized()`).
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
    Pulse.reportDeviceCrash(error, {
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
