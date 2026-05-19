import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";
import type { FC, ReactNode } from "react";
import { PulseRumUserSync } from "./PulseRumUserSync";
import { readPulseWebRumConfig } from "./pulseRumConfig";

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
      <PulseRumUserSync />
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
