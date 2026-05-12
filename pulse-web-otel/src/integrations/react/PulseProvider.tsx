"use client";

import React, { createContext, useContext, useEffect, type JSX } from "react";
import { Pulse } from "../../sdk";
import type { PulseProviderProps } from "../../types/react";
import { PulseErrorBoundary } from "./PulseErrorBoundary";

export type { PulseProviderProps } from "../../types/react";

export type PulseContextValue = typeof Pulse;

const PulseContext = createContext<PulseContextValue | null>(null);
PulseContext.displayName = "PulseContext";

// Module-level mount counter survives React 18 StrictMode's synthetic
// unmount/remount in dev. Cleanup is deferred by a microtask; shutdown only
// fires when the counter settles at 0 in the same microtask.
let providerMountCount = 0;

/**
 * React bridge that calls {@link Pulse.init} exactly once on mount and
 * exposes the SDK via context. Safe to render at the app root.
 *
 * SSR: the effect is skipped on the server; no browser APIs are touched.
 *
 * StrictMode: safe. Repeated mount/unmount cycles do not re-initialise.
 */
export function PulseProvider({
  config,
  children,
  shutdownOnUnmount = false,
  errorBoundaryFallback,
}: PulseProviderProps): JSX.Element {
  useEffect(() => {
    if (typeof window === "undefined") return;

    providerMountCount += 1;
    if (!Pulse.isInitialized()) {
      Pulse.init(config);
    }

    return (): void => {
      providerMountCount -= 1;
      if (!shutdownOnUnmount) return;
      // Defer: StrictMode re-mounts synchronously after cleanup in dev.
      // Only shutdown if no re-mount landed in the same task.
      queueMicrotask(() => {
        if (providerMountCount === 0 && Pulse.isInitialized()) {
          void Pulse.shutdown();
        }
      });
    };
    // config changes after first mount are intentionally ignored — Pulse
    // is a process-wide singleton, re-starting with a new config requires a
    // full unmount + remount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <PulseContext.Provider value={Pulse}>
      <PulseErrorBoundary fallback={errorBoundaryFallback}>
        {children}
      </PulseErrorBoundary>
    </PulseContext.Provider>
  );
}

/**
 * Access the Pulse SDK instance from React components. Throws a helpful
 * error when called outside a {@link PulseProvider}.
 */
export function usePulse(): PulseContextValue {
  const ctx = useContext(PulseContext);
  if (ctx === null) {
    throw new Error(
      "usePulse() must be called inside <PulseProvider>. " +
        "Wrap your app root with <PulseProvider config={...}>...</PulseProvider>.",
    );
  }
  return ctx;
}

// Test-only: reset the module-level mount counter between tests. Not part
// of the public API; consumers must not rely on this symbol.
export function _resetPulseProviderStateForTesting(): void {
  providerMountCount = 0;
}
