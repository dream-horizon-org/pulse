import React, { Component, type ErrorInfo, type ReactNode } from "react";
import { PulseWebLogger } from "../../pulse-web-logger";
import { Pulse } from "../../sdk";
import type { PulseErrorBoundaryProps } from "../../types/react";

export type { PulseErrorBoundaryProps } from "../../types/react";

interface State {
  hasError: boolean;
  error: Error | null;
}

interface FallbackHostState {
  fallbackRenderFailed: boolean;
}

const FALLBACK_FAIL_MSG =
  "errorBoundaryFallback failed to render (e.g. missing React context such as Router). " +
  "Rendering nothing so the host app is not crashed.";

interface FallbackInvokerProps {
  error: Error;
  reset: () => void;
  fallback: PulseErrorBoundaryProps["fallback"];
}

/** Invokes fallback inside the subtree guarded by {@link PulseErrorBoundaryFallbackHost}. */
function PulseErrorBoundaryFallbackInvoker({
  error,
  reset,
  fallback,
}: FallbackInvokerProps): ReactNode {
  if (typeof fallback === "function") {
    return fallback(error, reset);
  }
  return fallback ?? null;
}

/**
 * Isolates {@link PulseErrorBoundary}'s optional fallback UI. If the fallback throws
 * (invalid hooks, missing providers), we log and render null - never propagate.
 */
class PulseErrorBoundaryFallbackHost extends Component<
  { children: ReactNode },
  FallbackHostState
> {
  state: FallbackHostState = { fallbackRenderFailed: false };

  static getDerivedStateFromError(): FallbackHostState {
    return { fallbackRenderFailed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    const stack = info.componentStack?.trim();
    PulseWebLogger.alwaysError(
      stack
        ? `${FALLBACK_FAIL_MSG} componentStack:${stack}`
        : FALLBACK_FAIL_MSG,
      error,
    );
  }

  render(): ReactNode {
    if (this.state.fallbackRenderFailed) {
      return null;
    }
    return this.props.children;
  }
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
      return (
        <PulseErrorBoundaryFallbackHost>
          <PulseErrorBoundaryFallbackInvoker
            error={this.state.error}
            reset={this.reset}
            fallback={fallback}
          />
        </PulseErrorBoundaryFallbackHost>
      );
    }
    return this.props.children;
  }
}
