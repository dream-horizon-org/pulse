import {
  PulseDataCollectionConsent,
  type PulseWebConfig,
} from "@dreamhorizonorg/pulse-web";
import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";
import type { FC, ReactNode } from "react";
import packageJson from "../../package.json";

function readPulseWebRumConfig(): PulseWebConfig | null {
  const apiKey = process.env.REACT_APP_PULSE_WEB_API_KEY?.trim();
  if (!apiKey) {
    return null;
  }
  const endpoint = process.env.REACT_APP_PULSE_WEB_OTLP_ENDPOINT?.trim();
  return {
    apiKey,
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    serviceName: "pulse-ui",
    serviceVersion: packageJson.version,
    ...(endpoint ? { endpoint } : {}),
  };
}

/**
 * Initializes Pulse Web RUM when {@code REACT_APP_PULSE_WEB_API_KEY} is set; otherwise passes children through.
 */
export const PulseRumProvider: FC<{ children: ReactNode }> = ({ children }) => {
  const config = readPulseWebRumConfig();
  if (!config) {
    return <>{children}</>;
  }
  return <PulseProvider config={config}>{children}</PulseProvider>;
};

/**
 * Mount under {@code BrowserRouter} when using {@link PulseRumProvider}. No-op when RUM is disabled.
 */
export const PulseRumRouterEvents: FC = () => {
  return readPulseWebRumConfig() ? <PulseRouterEvents /> : null;
};
