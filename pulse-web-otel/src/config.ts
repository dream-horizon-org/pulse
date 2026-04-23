import type { PulseWebConfig } from "./types/config";
import { validateBeforeSendConfig } from "./before-send";

export { PulseDataCollectionConsent } from "./types/config";
export type {
  InstrumentationConfig,
  PulseWebDiskBufferingConfig,
  PulseWebConfig,
} from "./types/config";
export type {
  PulseWebBeforeSendCallbacks,
  PulseWebBeforeSendConfig,
} from "./types/before-send";
export { PulseLogLevel } from "./pulse-log-level";

export function validateConfig(config: PulseWebConfig): void {
  if (!config.apiKey) throw new Error("[PulseWeb] apiKey is required");
  validateBeforeSendConfig(config.beforeSendData);
  const diskOn = config.diskBuffering?.enabled !== false;
  const disk = config.diskBuffering;
  if (diskOn && disk !== undefined) {
    if (
      disk.maxAgeMs !== undefined &&
      (!Number.isFinite(disk.maxAgeMs) || disk.maxAgeMs <= 0)
    ) {
      throw new Error(
        "[PulseWeb] diskBuffering.maxAgeMs must be a positive finite number",
      );
    }
    if (
      disk.maxCacheSizeBytes !== undefined &&
      (!Number.isFinite(disk.maxCacheSizeBytes) || disk.maxCacheSizeBytes <= 0)
    ) {
      throw new Error(
        "[PulseWeb] diskBuffering.maxCacheSizeBytes must be a positive finite number",
      );
    }
  }
}

export const PULSE_PROD_ENDPOINT_URL =
  "https://pulse-otel-collector.pulse-ux.com";

/**
 * Mirrors Android's PulseSDKInternal.isApiLocalDev().
 * Matches: default-project_* OR Test-*_*
 */
export function isLocalEnvironment(apiKey: string): boolean {
  return /^default-project_.*|^Test-.*_.*/.test(apiKey);
}

/**
 * Mirrors Android's PulseEndpointUtils.getBaseUrl().
 * Local: http://localhost:4318
 * Prod: https://pulse-otel-collector.pulse-ux.com
 * Internal only — not part of the public config surface.
 */
export function resolveEndpointBaseUrl(
  apiKey: string,
  provided?: string,
): string {
  if (provided) return provided;
  if (isLocalEnvironment(apiKey)) {
    return "http://localhost:4318";
  }
  return PULSE_PROD_ENDPOINT_URL;
}
