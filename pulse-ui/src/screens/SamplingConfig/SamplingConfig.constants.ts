/**
 * SDK Configuration Constants
 * Aligned with Backend PulseConfig Schema
 *
 * Note: SDK, Scope, Rule, and Feature options are fetched dynamically from backend APIs:
 * - GET /v1/configs/rules-features -> rules and features
 * - GET /v1/configs/scopes-sdks -> scopes and SDKs
 *
 * The constants below are fallbacks and display helpers.
 */

import {
  PulseConfig,
  SdkEnum,
  ScopeEnum,
  SamplingRuleName,
  FeatureName,
  PipelineStats,
} from "./SamplingConfig.interface";

// ============================================================================
// DISPLAY HELPERS - Maps enum values to human-readable labels and colors
// These are used when backend doesn't provide display info
// ============================================================================

export const SDK_DISPLAY_INFO: Record<
  string,
  { label: string; color: string }
> = {
  pulse_android_java: { label: "Pulse Android Java", color: "#3DDC84" },
  pulse_android_rn: { label: "Pulse Android RN", color: "#61DAFB" },
  pulse_ios_swift: { label: "Pulse iOS Swift", color: "#007AFF" },
  pulse_ios_rn: { label: "Pulse iOS RN", color: "#61DAFB" },
};

export const SCOPE_DISPLAY_INFO: Record<
  string,
  { label: string; color: string }
> = {
  logs: { label: "Logs", color: "#10B981" },
  traces: { label: "Traces", color: "#8B5CF6" },
  metrics: { label: "Metrics", color: "#F59E0B" },
  baggage: { label: "Baggage", color: "#EC4899" },
};

export const RULE_DISPLAY_INFO: Record<
  string,
  { label: string; description: string }
> = {
  os_version: {
    label: "OS Version",
    description: "Match by operating system version",
  },
  app_version: {
    label: "App Version",
    description: "Match by application version",
  },
  country: {
    label: "Country",
    description: "Match by user country (ISO code)",
  },
  platform: { label: "Platform", description: "Match by device platform" },
  state: {
    label: "State",
    description: "Match by geo state/region (ISO code)",
  },
  device: { label: "Device", description: "Match by device model" },
  network: { label: "Network", description: "Match by network type" },
};

export const FEATURE_DISPLAY_INFO: Record<
  string,
  { label: string; description: string; icon: string }
> = {
  interaction: {
    label: "User Interactions",
    description: "Capture user journeys",
    icon: "click",
  },
  java_crash: {
    label: "Java Crash",
    description: "Capture Java/Kotlin crashes",
    icon: "bug",
  },
  js_crash: {
    label: "JS Crash",
    description: "Capture JavaScript crashes",
    icon: "bug",
  },
  java_anr: {
    label: "Java ANR",
    description: "Capture Application Not Responding events",
    icon: "alert",
  },
  network_change: {
    label: "Network Change",
    description: "Track network state changes",
    icon: "wifi",
  },
  screen_session: {
    label: "Screen Session",
    description: "Track screen views and sessions",
    icon: "screen",
  },
  custom_events: {
    label: "Custom Events",
    description: "User-defined custom events",
    icon: "tag",
  },
  rn_screen_load: {
    label: "React Native Screen Load",
    description: "Track React Native Screen Loads",
    icon: "navigation",
  },
  rn_screen_interactive: {
    label: "React Native Screen Interactive",
    description: "Track React Native Screen Interactive Events",
    icon: "navigation",
  },
  session_replay: {
    label: "Session Replay",
    description:
      "Record and replay user sessions with configurable PII masking",
    icon: "replay",
  },
  android_network: {
    label: "Android Network",
    description: "Track Android network requests and performance",
    icon: "network",
  },
  ios_network: {
    label: "iOS Network",
    description: "Track iOS network requests and performance",
    icon: "network",
  },
  rn_network: {
    label: "React Native Network",
    description: "Track React Native network requests and performance",
    icon: "network",
  },
  ios_crash: {
    label: "iOS Crash",
    description: "Capture iOS/Swift crashes and exceptions",
    icon: "bug",
  },
  ios_lifecycle: {
    label: "iOS Lifecycle",
    description: "Track iOS app lifecycle events and view controllers",
    icon: "navigation",
  },
  android_activity: {
    label: "Android Activity",
    description: "Track Android activity lifecycle events",
    icon: "navigation",
  },
  android_fragment: {
    label: "Android Fragment",
    description: "Track Android fragment lifecycle events",
    icon: "navigation",
  },
  android_slowrendering: {
    label: "Android Slow Rendering",
    description: "Detect and report slow rendering and jank events",
    icon: "alert",
  },
  location: {
    label: "Location",
    description: "Track user location data",
    icon: "navigation",
  },
};

export const SESSION_REPLAY_FEATURE_NAME = "session_replay" as const;

// ============================================================================
// PROPERTY MATCH OPERATORS - Simplified pattern matching for UX
// These help users build regex patterns without knowing regex syntax
// ============================================================================
export type PropertyMatchOperator =
  | "equals"
  | "contains"
  | "starts_with"
  | "ends_with"
  | "regex"
  | "not_equals";

export const PROPERTY_MATCH_OPERATORS: {
  value: PropertyMatchOperator;
  label: string;
  toRegex: (val: string) => string;
}[] = [
  {
    value: "equals",
    label: "Equals",
    toRegex: (val) => `^${escapeRegex(val)}$`,
  },
  {
    value: "not_equals",
    label: "Not Equals",
    toRegex: (val) => `^(?!${escapeRegex(val)}$).*$`,
  },
  {
    value: "contains",
    label: "Contains",
    toRegex: (val) => `.*${escapeRegex(val)}.*`,
  },
  {
    value: "starts_with",
    label: "Starts With",
    toRegex: (val) => `^${escapeRegex(val)}.*`,
  },
  {
    value: "ends_with",
    label: "Ends With",
    toRegex: (val) => `.*${escapeRegex(val)}$`,
  },
  { value: "regex", label: "Regex Pattern", toRegex: (val) => val },
];

// Helper to escape special regex characters
function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

// Helper to unescape regex characters (reverse of escapeRegex)
function unescapeRegex(str: string): string {
  return str.replace(/\\([.*+?^${}()|[\]\\])/g, "$1");
}

/**
 * Detect operator and extract raw value from a regex pattern
 * Returns { operator, rawValue } for use when editing attribute condition regexes
 */
export function detectOperatorFromRegex(regexPattern: string): {
  operator: PropertyMatchOperator;
  rawValue: string;
} {
  // Try to match common patterns we generate

  // Equals: ^escapedValue$
  const equalsMatch = regexPattern.match(/^\^(.+)\$$/);
  if (
    equalsMatch &&
    !regexPattern.includes(".*") &&
    !regexPattern.includes("(?!")
  ) {
    return { operator: "equals", rawValue: unescapeRegex(equalsMatch[1]) };
  }

  // Not Equals: ^(?!escapedValue$).*$
  const notEqualsMatch = regexPattern.match(/^\^\(\?!(.+)\$\)\.\*\$$/);
  if (notEqualsMatch) {
    return {
      operator: "not_equals",
      rawValue: unescapeRegex(notEqualsMatch[1]),
    };
  }

  // Contains: .*escapedValue.*
  const containsMatch = regexPattern.match(/^\.\*(.+)\.\*$/);
  if (containsMatch) {
    return { operator: "contains", rawValue: unescapeRegex(containsMatch[1]) };
  }

  // Starts With: ^escapedValue.*
  const startsWithMatch = regexPattern.match(/^\^(.+)\.\*$/);
  if (startsWithMatch) {
    return {
      operator: "starts_with",
      rawValue: unescapeRegex(startsWithMatch[1]),
    };
  }

  // Ends With: .*escapedValue$
  const endsWithMatch = regexPattern.match(/^\.\*(.+)\$$/);
  if (endsWithMatch) {
    return { operator: "ends_with", rawValue: unescapeRegex(endsWithMatch[1]) };
  }

  // Default: treat as raw regex
  return { operator: "regex", rawValue: regexPattern };
}

// ============================================================================
// HELPER FUNCTIONS FOR DYNAMIC OPTIONS
// ============================================================================

/**
 * Convert backend SDK list to select options
 */
export const toSdkOptions = (
  sdks: string[],
): { value: SdkEnum; label: string; color: string }[] => {
  return sdks.map((sdk) => ({
    value: sdk as SdkEnum,
    label: SDK_DISPLAY_INFO[sdk]?.label || formatEnumLabel(sdk),
    color: SDK_DISPLAY_INFO[sdk]?.color || "#6B7280",
  }));
};

/**
 * Convert backend Scope list to select options
 */
export const toScopeOptions = (
  scopes: string[],
): { value: ScopeEnum; label: string; color: string }[] => {
  return scopes.map((scope) => ({
    value: scope as ScopeEnum,
    label: SCOPE_DISPLAY_INFO[scope]?.label || formatEnumLabel(scope),
    color: SCOPE_DISPLAY_INFO[scope]?.color || "#6B7280",
  }));
};

/**
 * Convert backend rules list to select options
 */
export const toRuleOptions = (
  rules: string[],
): { value: SamplingRuleName; label: string; description: string }[] => {
  return rules.map((rule) => ({
    value: rule as SamplingRuleName,
    label: RULE_DISPLAY_INFO[rule]?.label || formatEnumLabel(rule),
    description:
      RULE_DISPLAY_INFO[rule]?.description ||
      `Match by ${formatEnumLabel(rule).toLowerCase()}`,
  }));
};

/**
 * Convert backend features list to select options
 */
export const toFeatureOptions = (
  features: string[],
): {
  value: FeatureName;
  label: string;
  description: string;
  icon: string;
}[] => {
  return features.map((feature) => ({
    value: feature as FeatureName,
    label: FEATURE_DISPLAY_INFO[feature]?.label || formatEnumLabel(feature),
    description:
      FEATURE_DISPLAY_INFO[feature]?.description ||
      `SDK feature: ${formatEnumLabel(feature)}`,
    icon: FEATURE_DISPLAY_INFO[feature]?.icon || "settings",
  }));
};

/**
 * Convert snake_case enum to Title Case label
 */
function formatEnumLabel(value: string): string {
  return value
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

// Generate unique IDs for UI tracking (stripped before API calls)
export const generateId = () => Math.random().toString(36).substring(2, 11);

/**
 * Validate a regex pattern string
 * Returns null if valid, error message if invalid
 */
export const validateRegex = (pattern: string): string | null => {
  if (!pattern.trim()) {
    return null; // Empty is valid (will be handled by required field validation)
  }

  try {
    new RegExp(pattern);
    return null; // Valid regex
  } catch (e) {
    if (e instanceof SyntaxError) {
      // Extract the useful part of the error message
      const message = e.message.replace(
        /^Invalid regular expression: \/.*\/: /,
        "",
      );
      return `Invalid regex: ${message}`;
    }
    return "Invalid regex pattern";
  }
};

/**
 * Format a regex-based name field for display in the UI
 * Shows a human-readable representation of the pattern
 */
export const formatNameForDisplay = (regexName: string): string => {
  if (!regexName) return "(any)";

  const detected = detectOperatorFromRegex(regexName);
  const operatorLabel =
    PROPERTY_MATCH_OPERATORS.find((op) => op.value === detected.operator)
      ?.label || "Matches";

  if (detected.operator === "regex") {
    // For raw regex, show a truncated version
    const truncated =
      detected.rawValue.length > 30
        ? detected.rawValue.substring(0, 30) + "..."
        : detected.rawValue;
    return `Regex: ${truncated}`;
  }

  // For standard operators, show "Value (Operator)"
  const truncatedValue =
    detected.rawValue.length > 25
      ? detected.rawValue.substring(0, 25) + "..."
      : detected.rawValue;

  if (detected.operator === "equals") {
    return truncatedValue; // For exact match, just show the value
  }

  return `${truncatedValue} (${operatorLabel})`;
};

// ============================================================================
// DEFAULT CONFIGURATION - Matches backend PulseConfig schema
// ============================================================================
export const DEFAULT_PULSE_CONFIG: PulseConfig = {
  description: "Default SDK configuration",
  sampling: {
    default: {
      sessionSampleRate: 1.0, // 100% by default
    },
    rules: [],
    criticalSessionPolicies: {
      alwaysSend: [],
    },
  },
  signals: {
    scheduleDurationMs: 5000,
    logsCollectorUrl: "http://10.0.2.2:4318/v1/logs",
    metricCollectorUrl: "http://10.0.2.2:4318/v1/metrics",
    spanCollectorUrl: "http://10.0.2.2:4318/v1/traces",
    customEventCollectorUrl: "http://10.0.2.2:4318/v1/events",
    attributesToDrop: [],
    attributesToAdd: [],
  },
  interaction: {
    collectorUrl: "http://10.0.2.2:4318/v1/traces",
    configUrl: "http://10.0.2.2:8080/v1/interaction-configs/",
    beforeInitQueueSize: 100,
  },
  features: [],
};

// ============================================================================
// CALCULATE PIPELINE STATS
// ============================================================================
export const calculatePipelineStats = (config: PulseConfig): PipelineStats => {
  const baseEvents = 100000; // Simulated monthly events

  // Sampling rate from default session sample rate
  const samplingRate = config.sampling.default.sessionSampleRate * 100;
  const afterSampling = baseEvents * (samplingRate / 100);

  // Feature gates - features control which types of data are collected
  // If no features configured, assume 100% of data passes (no feature-level filtering)
  // If features are configured, enabled features contribute to data sent
  const totalFeatures = config.features.length;
  const enabledFeatures = config.features.filter(
    (f) => f.sessionSampleRate === 1,
  );

  let featurePassRate: number;
  if (totalFeatures === 0) {
    // No features configured = all data passes through (no feature filtering)
    featurePassRate = 100;
  } else {
    // Features are configured - show % of enabled features
    // This is conceptual - each enabled feature type sends its data
    featurePassRate = Math.round(
      (enabledFeatures.length / totalFeatures) * 100,
    );
    // Minimum 10% if at least one feature is enabled
    if (enabledFeatures.length > 0 && featurePassRate < 10) {
      featurePassRate = 10;
    }
  }

  const featureDropRate = 100 - featurePassRate;
  const afterFeatures = afterSampling * (featurePassRate / 100);

  // Final sent is after all stages
  const finalSent = Math.round(afterFeatures);

  return {
    totalEvents: baseEvents,
    afterSampling: Math.round(afterSampling),
    afterFeatures: Math.round(afterFeatures),
    finalSent: finalSent,
    samplingDropRate: Math.round(100 - samplingRate),
    featureDropRate: Math.round(featureDropRate),
    totalSentRate:
      baseEvents > 0 ? Math.round((finalSent / baseEvents) * 100) : 0,
  };
};

// ============================================================================
// UI TEXT CONSTANTS
// ============================================================================
export const UI_CONSTANTS = {
  PAGE_TITLE: "SDK Configuration",
  PAGE_SUBTITLE: "Control what data your app sends to Pulse",

  SECTIONS: {
    ATTRIBUTES_TO_DROP: {
      TITLE: "Attributes to Drop",
      DESCRIPTION:
        "Drop specific attributes from events (for privacy/data reduction)",
    },
    SAMPLING: {
      TITLE: "Sampling Configuration",
      DESCRIPTION: "Control what percentage of sessions are sampled",
    },
    SAMPLING_RULES: {
      TITLE: "Sampling Rules",
      DESCRIPTION: "Apply different sample rates based on device parameters",
    },
    FEATURES: {
      TITLE: "Feature Configuration",
      DESCRIPTION: "Enable/disable SDK features per platform",
    },
    SIGNALS: {
      TITLE: "Signals Configuration",
      DESCRIPTION:
        "Infrastructure settings for telemetry collection (auto-configured)",
    },
    INTERACTION: {
      TITLE: "Interaction Configuration",
      DESCRIPTION:
        "Infrastructure settings for interaction tracking (auto-configured)",
    },
  },

  ACTIONS: {
    SAVE: "Save Configuration",
    RESET: "Reset to Defaults",
    SAVING: "Saving...",
    VIEW_JSON: "View JSON",
  },

  NOTIFICATIONS: {
    SAVE_SUCCESS: "Configuration saved successfully",
    SAVE_ERROR: "Failed to save configuration",
    LOAD_ERROR: "Failed to load configuration",
    RESET_SUCCESS: "Configuration reset to defaults",
  },
};

// ============================================================================
// HELPER FUNCTIONS FOR API PAYLOADS
// ============================================================================

/**
 * Strips UI-only fields (like id) from config before sending to API
 */
export const stripUIFields = (config: PulseConfig): PulseConfig => {
  console.log("stripUIFields", config);
  const cleanConfig = JSON.parse(JSON.stringify(config));

  // Remove id fields from attributesToDrop and their conditions
  cleanConfig.signals.attributesToDrop.forEach(
    (a: { id?: string; condition?: { id?: string } }) => {
      delete a.id;
      if (a.condition) delete a.condition.id;
    },
  );

  // Remove id fields from attributesToAdd and their conditions
  cleanConfig.signals.attributesToAdd?.forEach(
    (a: { id?: string; condition?: { id?: string } }) => {
      delete a.id;
      if (a.condition) delete a.condition.id;
    },
  );

  // Remove id fields from sampling rules
  cleanConfig.sampling.rules.forEach((r: { id?: string }) => delete r.id);

  // Remove id fields from critical session policies
  cleanConfig.sampling.criticalSessionPolicies.alwaysSend.forEach(
    (p: { id?: string }) => delete p.id,
  );

  // Remove id fields from features
  cleanConfig.features.forEach((f: { id?: string }) => delete f.id);

  return cleanConfig;
};

/**
 * Adds UI tracking IDs to config items loaded from API
 */
export const addUIIds = (config: PulseConfig): PulseConfig => {
  const configWithIds = JSON.parse(JSON.stringify(config));

  // Add id fields to attributesToDrop
  configWithIds.signals.attributesToDrop =
    configWithIds.signals.attributesToDrop.map((a: object) => ({
      ...a,
      id: generateId(),
    }));

  // Add id fields to attributesToAdd
  if (configWithIds.signals.attributesToAdd) {
    configWithIds.signals.attributesToAdd =
      configWithIds.signals.attributesToAdd.map((a: object) => ({
        ...a,
        id: generateId(),
      }));
  }

  // Add id fields to sampling rules
  configWithIds.sampling.rules = configWithIds.sampling.rules.map(
    (r: object) => ({ ...r, id: generateId() }),
  );

  // Add id fields to critical session policies
  if (configWithIds.sampling.criticalSessionPolicies) {
    configWithIds.sampling.criticalSessionPolicies.alwaysSend =
      configWithIds.sampling.criticalSessionPolicies.alwaysSend.map(
        (p: object) => ({ ...p, id: generateId() }),
      );
  }

  // Add id fields to features
  configWithIds.features = configWithIds.features.map((f: object) => ({
    ...f,
    id: generateId(),
  }));

  return configWithIds;
};
