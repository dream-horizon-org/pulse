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

export interface PulseFeatureConfig {
  featureName: PulseFeatureName;
  sessionSampleRate: number;
  sdks: PulseSdkName[];
  config?: Record<string, unknown>;
}

export interface PulseSignalMatchCondition {
  name: string;
  props: Array<{ key: string; value: string }>;
  scopes: Array<"LOGS" | "TRACES" | "METRICS">;
  sdks: PulseSdkName[];
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
