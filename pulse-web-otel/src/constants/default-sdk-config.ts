import type { PulseSdkConfig } from "../types/remote-config";

export const DEFAULT_SDK_CONFIG: PulseSdkConfig = {
  version: -1,
  sampling: {
    default: { sessionSampleRate: 1.0 },
    rules: [],
    signalsToSample: [],
  },
  signals: {
    scheduleDurationMs: 5000,
    attributesToDrop: [],
    attributesToAdd: [],
    filters: { mode: "BLACKLIST", values: [] },
    metricsToAdd: [],
  },
  interaction: { beforeInitQueueSize: 5000 },
  features: [],
};
