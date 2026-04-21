export {
  PulseDataCollectionConsent,
  type InstrumentationConfig,
  type PulseWebConfig,
} from "./types/config";

import type { PulseWebConfig } from "./types/config";

export function validateConfig(config: PulseWebConfig): void {
  if (!config.endpointBaseUrl)
    throw new Error("[PulseWeb] endpointBaseUrl is required");
  if (!config.apiKey) throw new Error("[PulseWeb] apiKey is required");
  if (!config.serviceName)
    throw new Error("[PulseWeb] serviceName is required");
}
