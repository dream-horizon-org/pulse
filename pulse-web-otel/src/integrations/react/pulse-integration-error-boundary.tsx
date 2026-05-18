"use client";

import React, { Component, type ErrorInfo, type ReactNode } from "react";
import { PulseWebLogger } from "../../pulse-web-logger";
import { PULSE_ROUTER_LOG_PREFIX } from "./apply-pulse-screen-navigation";

export type PulseIntegrationErrorContext =
  | "react-router"
  | "next-app"
  | "next-pages";

interface Props {
  context: PulseIntegrationErrorContext;
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

function integrationFatalMessage(ctx: PulseIntegrationErrorContext): string {
  switch (ctx) {
    case "react-router":
      return (
        `${PULSE_ROUTER_LOG_PREFIX} PulseRouterEvents failed during render. ` +
        "Mount under <BrowserRouter> / <MemoryRouter>. " +
        "On Next.js App Router use <PulseRouterEvents /> from @dreamhorizonorg/pulse-web/next, " +
        "not @dreamhorizonorg/pulse-web/react/router without a Router."
      );
    case "next-app":
      return (
        `${PULSE_ROUTER_LOG_PREFIX} Next.js App Router tracking failed during render. ` +
        "Ensure <PulseRouterEvents /> runs in a Client Component inside the app tree " +
        "(wrapped in Suspense for useSearchParams)."
      );
    case "next-pages":
      return (
        `${PULSE_ROUTER_LOG_PREFIX} Next.js Pages Router tracking failed during render. ` +
        "Ensure useNextPagesRouterTracking() runs under the Pages Router (_app / client page)."
      );
    default:
      return `${PULSE_ROUTER_LOG_PREFIX} integration failed during render.`;
  }
}

/**
 * Catches React **render** errors from router integration so the host app is not unmounted.
 * Logs via {@link PulseWebLogger.alwaysError} regardless of SDK log level.
 */
export class PulseIntegrationErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    const stack = info.componentStack?.trim();
    const base = integrationFatalMessage(this.props.context);
    PulseWebLogger.alwaysError(
      stack ? `${base} componentStack: ${stack}` : base,
      error,
    );
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return null;
    }
    return this.props.children;
  }
}
