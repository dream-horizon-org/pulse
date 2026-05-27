import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";
import { useEffect, type FC, type ReactNode } from "react";
import { readPulseWebRumConfig } from "./pulseRumConfig";
import {
  flushPendingPulseEvents,
  flushPulseUserIdentityWhenReady,
  syncPulseUserIdentityFromCookies,
} from "./pulseRum";

/**
 * Re-applies user identity from cookies after refresh / deep link,
 * and drains pending events after async {@link Pulse.init}.
 * Must render inside {@link PulseProvider}.
 */
const InitEffect: FC = () => {
  useEffect(() => {
    syncPulseUserIdentityFromCookies();
    void flushPulseUserIdentityWhenReady();
    void flushPendingPulseEvents();
  }, []);

  return null;
};

/**
 * Initializes Pulse Web RUM when {@code REACT_APP_PULSE_WEB_API_KEY} is set; otherwise passes children through.
 */
export const PulseRumProvider: FC<{ children: ReactNode }> = ({ children }) => {
  const config = readPulseWebRumConfig();
  if (!config) {
    return <>{children}</>;
  }
  return (
    <PulseProvider config={config}>
      <InitEffect />
      {children}
    </PulseProvider>
  );
};

/**
 * Mount under {@code BrowserRouter} when using {@link PulseRumProvider}. No-op when RUM is disabled.
 */
export const PulseRumRouterEvents: FC = () => {
  return readPulseWebRumConfig() ? <PulseRouterEvents /> : null;
};
