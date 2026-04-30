export type PulseSdkName =
  | "pulse_android_java"
  | "pulse_android_rn"
  | "pulse_ios_swift"
  | "pulse_ios_rn"
  | "pulse_web_js";

export type PulseFeatureName =
  | "js_crash"
  | "network_instrumentation"
  | "click"
  | "web_vitals"
  | "screen_session"
  | "long_task"
  | "resource_timing"
  | "visibility"
  | "websocket"
  | "bfcache"
  | "interaction"
  | "session_replay"
  | "network_change"
  | "custom_events"
  | "session";

export const PulseFeature = {
  JS_CRASH: "js_crash",
  NETWORK_INSTRUMENTATION: "network_instrumentation",
  CLICK: "click",
  WEB_VITALS: "web_vitals",
  SCREEN_SESSION: "screen_session",
  LONG_TASK: "long_task",
  RESOURCE_TIMING: "resource_timing",
  VISIBILITY: "visibility",
  WEBSOCKET: "websocket",
  BFCACHE: "bfcache",
  INTERACTION: "interaction",
  SESSION_REPLAY: "session_replay",
  NETWORK_CHANGE: "network_change",
  CUSTOM_EVENTS: "custom_events",
  SESSION: "session",
} as const;

export interface PulseFeatureConfig {
  featureName: PulseFeatureName;
  sessionSampleRate: number;
  sdks: PulseSdkName[];
  config?: Record<string, unknown>;
}

export interface PulseSignalMatchCondition {
  name: string;
  /** Attribute key/value regexes; backend JSON may use `name` instead of `key` on each prop — normalized at merge. */
  props: Array<{ key: string; value: string }>;
  scopes: Array<"LOGS" | "TRACES" | "METRICS">;
  sdks: PulseSdkName[];
}

/** Polymorphic `signals.metricsToAdd[].target` — JSON discriminator `type`. */
export type PulseMetricsToAddTarget =
  | { type: "name" }
  | {
      type: "attribute";
      condition: PulseSignalMatchCondition;
      shouldAddPropNameAsSuffix?: boolean;
    };

/** Polymorphic `signals.metricsToAdd[].type` — JSON discriminator `type`. */
export type PulseMetricsType =
  | { type: "counter" }
  | { type: "gauge"; isFraction: boolean }
  | {
      type: "histogram";
      bucket?: number[] | null;
      isFraction: boolean;
    }
  | { type: "sum"; isFraction: boolean; isMonotonic: boolean };

export interface PulseMetricsToAddEntry {
  name: string;
  target: PulseMetricsToAddTarget;
  condition: PulseSignalMatchCondition;
  type: PulseMetricsType;
  attributesToPick?: PulseSignalMatchCondition[];
}

export interface PulseAttributeValue {
  name: string;
  value: string;
  type: "STRING" | "BOOLEAN" | "LONG" | "DOUBLE" | "STRING_ARRAY";
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
  mode: "BLACKLIST" | "WHITELIST";
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
  /** Derived OTel metrics from matching spans/logs at export time (Android parity). */
  metricsToAdd: PulseMetricsToAddEntry[];
}

/** Per-signal sampling override (Android `signalsToSample`). */
export interface PulseSignalsToSampleEntry {
  condition: PulseSignalMatchCondition;
  sampleRate: number;
}

export interface PulseSamplingConfig {
  default: { sessionSampleRate: number };
  rules: PulseSessionSamplingRule[];
  /** Matches pulse-server `SamplingConfig.criticalSessionPolicies` — `alwaysSend` bypasses session sampling / runs before signal filters in `ExportSamplingGate`. */
  criticalSessionPolicies?: { alwaysSend: PulseSignalMatchCondition[] };
  /** Android `signalsToSample` — optional per-signal rates after attr pipeline. */
  signalsToSample?: PulseSignalsToSampleEntry[];
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
