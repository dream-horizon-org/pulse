// M1/M2: SdkConfigFetcher — loads cached config from localStorage on init,
// fetches fresh config in the background from /v1/configs/active.
// See: web-sdk-plan/v1/01-foundation/sdk-config.md

const SDK_CONFIG_CACHE_KEY = 'pulse_sdk_config';

export type PulseSdkName =
  | 'pulse_android_java'
  | 'pulse_android_rn'
  | 'pulse_ios_swift'
  | 'pulse_ios_rn'
  | 'pulse_web_js';

export type PulseFeatureName =
  | 'js_crash'
  | 'network_instrumentation'
  | 'click'
  | 'web_vitals'
  | 'screen_session'
  | 'long_task'
  | 'resource_timing'
  | 'visibility'
  | 'websocket'
  | 'bfcache'
  | 'interaction'
  | 'session_replay'
  | 'network_change'
  | 'custom_events'
  | 'session';

export interface PulseFeatureConfig {
  featureName: PulseFeatureName;
  sessionSampleRate: number;
  sdks: PulseSdkName[];
  config?: Record<string, unknown>;
}

export interface PulseSignalMatchCondition {
  name: string;
  props: Array<{ key: string; value: string }>;
  scopes: Array<'LOGS' | 'TRACES' | 'METRICS'>;
  sdks: PulseSdkName[];
}

export interface PulseAttributeValue {
  name: string;
  value: string;
  type: 'STRING' | 'BOOLEAN' | 'LONG' | 'DOUBLE' | 'STRING_ARRAY';
}

export interface PulseAttributesToDropEntry {
  values: string[];
  condition: PulseSignalMatchCondition;
}

export interface PulseAttributesToAddEntry {
  values: PulseAttributeValue[];
  condition: PulseSignalMatchCondition;
}

export interface PulseSignalFilter {
  mode: 'BLACKLIST' | 'WHITELIST';
  values: PulseSignalMatchCondition[];
}

export interface PulseSignalConfig {
  scheduleDurationMs: number;
  logsCollectorUrl?: string;
  metricCollectorUrl?: string;
  spanCollectorUrl?: string;
  attributesToDrop: PulseAttributesToDropEntry[];
  attributesToAdd: PulseAttributesToAddEntry[];
  filters: PulseSignalFilter;
}

export interface PulseSamplingConfig {
  default: { sessionSampleRate: number };
  rules: PulseSessionSamplingRule[];
  criticalEventPolicies?: { alwaysSend: PulseSignalMatchCondition[] };
}

export interface PulseSessionSamplingRule {
  name: string;
  value: string;
  sdks: PulseSdkName[];
  sessionSampleRate: number;
}

export interface PulseSdkConfig {
  version: number;
  description?: string;
  sampling: PulseSamplingConfig;
  signals: PulseSignalConfig;
  interaction: { beforeInitQueueSize: number };
  features: PulseFeatureConfig[];
}

export const DEFAULT_SDK_CONFIG: PulseSdkConfig = {
  version: -1,
  sampling: { default: { sessionSampleRate: 1.0 }, rules: [] },
  signals: {
    scheduleDurationMs: 5000,
    attributesToDrop: [],
    attributesToAdd: [],
    filters: { mode: 'BLACKLIST', values: [] },
  },
  interaction: { beforeInitQueueSize: 5000 },
  features: [],
};

function isValidSdkConfig(value: unknown): value is PulseSdkConfig {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v['version'] === 'number' &&
    typeof v['sampling'] === 'object' &&
    typeof v['signals'] === 'object' &&
    Array.isArray(v['features'])
  );
}

export class SdkConfigFetcher {
  private config: PulseSdkConfig = { ...DEFAULT_SDK_CONFIG };
  private readonly endpointBaseUrl: string;
  private readonly projectId: string;

  constructor(endpointBaseUrl: string, projectId: string) {
    this.endpointBaseUrl = endpointBaseUrl;
    this.projectId = projectId;
  }

  loadCached(): PulseSdkConfig {
    if (typeof window === 'undefined') return this.config;

    try {
      const raw = localStorage.getItem(SDK_CONFIG_CACHE_KEY);
      if (!raw) return this.config;

      const parsed: unknown = JSON.parse(raw);
      if (isValidSdkConfig(parsed)) {
        this.config = parsed;
      }
    } catch {
      // ignore parse/storage errors
    }

    return this.config;
  }

  async fetchInBackground(): Promise<void> {
    if (!this.endpointBaseUrl || !this.projectId) return;

    try {
      const url = `${this.endpointBaseUrl}/v1/configs/active/`;
      const response = await fetch(url, {
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) return;

      const data: unknown = await response.json();
      if (!isValidSdkConfig(data)) return;

      // Only update and persist if version has changed
      if (data.version !== this.config.version) {
        this.config = data;

        if (typeof window !== 'undefined') {
          try {
            localStorage.setItem(SDK_CONFIG_CACHE_KEY, JSON.stringify(data));
          } catch {
            // ignore storage errors
          }
        }
      }
    } catch {
      // ignore network errors — we continue with cached/default config
    }
  }

  getConfig(): PulseSdkConfig {
    return this.config;
  }
}
