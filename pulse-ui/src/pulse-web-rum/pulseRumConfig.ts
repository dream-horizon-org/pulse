import {
  PulseDataCollectionConsent,
  type PulseWebConfig,
} from "@dreamhorizonorg/pulse-web";
import packageJson from "../../package.json";

export function isPulseRumEnabled(): boolean {
  return Boolean(process.env.REACT_APP_PULSE_WEB_API_KEY?.trim());
}

export function readPulseWebRumConfig(): PulseWebConfig | null {
  const apiKey = process.env.REACT_APP_PULSE_WEB_API_KEY?.trim();
  if (!apiKey) {
    return null;
  }
  return {
    apiKey,
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    serviceName: "pulse-ui",
    serviceVersion: packageJson.version,
  };
}
