/**
 * SDK Configuration Constants
 * Matches the PulseConfig JSON Schema
 */

import {
  PulseConfig,
  SdkEnum,
  ScopeEnum,
  FilterMode,
  SamplingMatchType,
  PipelineStats,
} from './SamplingConfig.interface';

// SDK options for UI
export const SDK_OPTIONS: { value: SdkEnum; label: string; color: string }[] = [
  { value: 'android_native', label: 'Android Native', color: '#3DDC84' },
  { value: 'android_rn', label: 'Android RN', color: '#61DAFB' },
  { value: 'ios_native', label: 'iOS Native', color: '#007AFF' },
  { value: 'ios_rn', label: 'iOS RN', color: '#61DAFB' },
];

// Scope options for UI
export const SCOPE_OPTIONS: { value: ScopeEnum; label: string; color: string }[] = [
  { value: 'logs', label: 'Logs', color: '#10B981' },
  { value: 'traces', label: 'Traces', color: '#8B5CF6' },
  { value: 'metrics', label: 'Metrics', color: '#F59E0B' },
  { value: 'baggage', label: 'Baggage', color: '#EC4899' },
];

// Filter mode options
export const FILTER_MODE_OPTIONS: { value: FilterMode; label: string; description: string }[] = [
  { value: 'blacklist', label: 'Blacklist', description: 'Block matching events' },
  { value: 'whitelist', label: 'Whitelist', description: 'Only allow matching events' },
];

// Sampling match type options
export const SAMPLING_MATCH_TYPE_OPTIONS: { value: SamplingMatchType; label: string; description: string }[] = [
  { value: 'app_version_min', label: 'Min App Version', description: 'Apply to versions >= specified' },
  { value: 'app_version_max', label: 'Max App Version', description: 'Apply to versions <= specified' },
];

// Generate unique IDs for UI tracking
export const generateId = () => Math.random().toString(36).substring(2, 11);

// Default configuration matching schema
export const DEFAULT_PULSE_CONFIG: PulseConfig = {
  version: 1,
  filtersConfig: {
    mode: 'blacklist',
    whitelist: [],
    blacklist: [
      {
        id: generateId(),
        name: 'sensitive_event',
        props: [{ name: 'contains_pii', value: 'true' }],
        scope: ['logs', 'traces', 'metrics'],
        sdks: ['android_native', 'android_rn', 'ios_native', 'ios_rn'],
      },
      {
        id: generateId(),
        name: 'debug_log',
        props: [{ name: 'level', value: 'debug' }],
        scope: ['logs'],
        sdks: ['android_native', 'ios_native'],
      },
    ],
  },
  samplingConfig: {
    default: {
      session_sample_rate: 0.5,
    },
    rules: [
      {
        id: generateId(),
        name: 'high_value_users',
        match: {
          type: 'app_version_min',
          sdks: ['android_native', 'ios_native'],
          app_version_min_inclusive: '2.0.0',
        },
        session_sample_rate: 1.0,
      },
    ],
    criticalEventPolicies: {
      alwaysSend: [
        {
          id: generateId(),
          name: 'crash',
          props: [{ name: 'severity', value: 'critical' }],
          scope: ['traces', 'logs'],
        },
        {
          id: generateId(),
          name: 'payment_error',
          props: [{ name: 'error_type', value: 'payment.*' }],
          scope: ['traces'],
        },
      ],
    },
  },
  signals: {
    scheduleDurationMs: 5000,
    collectorUrl: 'https://collector.pulse.io/v1/traces',
    attributesToDrop: ['password', 'credit_card', 'ssn', 'auth_token'],
  },
  interaction: {
    collectorUrl: 'https://collector.pulse.io/v1/interactions',
    configUrl: 'https://config.pulse.io/v1/configs/latest',
    beforeInitQueueSize: 100,
  },
  featureConfigs: [
    {
      id: generateId(),
      featureName: 'crash_reporting',
      enabled: true,
      session_sample_rate: 1.0,
      sdks: ['android_native', 'android_rn', 'ios_native', 'ios_rn'],
    },
    {
      id: generateId(),
      featureName: 'network_monitoring',
      enabled: true,
      session_sample_rate: 0.8,
      sdks: ['android_native', 'android_rn', 'ios_native', 'ios_rn'],
    },
    {
      id: generateId(),
      featureName: 'performance_monitoring',
      enabled: true,
      session_sample_rate: 0.6,
      sdks: ['android_native', 'ios_native'],
    },
    {
      id: generateId(),
      featureName: 'user_interaction_tracking',
      enabled: false,
      session_sample_rate: 0.3,
      sdks: ['android_native', 'ios_native'],
    },
  ],
};

// Calculate pipeline stats based on config
export const calculatePipelineStats = (config: PulseConfig): PipelineStats => {
  const baseEvents = 100000; // Simulated monthly events
  
  // Calculate filter drop rate based on blacklist rules
  const filterDropRate = Math.min(config.filtersConfig.blacklist.length * 8, 40);
  const afterFilters = baseEvents * (1 - filterDropRate / 100);
  
  // Sampling rate from default
  const samplingRate = config.samplingConfig.default.session_sample_rate * 100;
  const afterSampling = afterFilters * (samplingRate / 100);
  
  // Feature impact - average of enabled features
  const enabledFeatures = config.featureConfigs.filter(f => f.enabled);
  const avgFeatureRate = enabledFeatures.length > 0
    ? (enabledFeatures.reduce((sum, f) => sum + f.session_sample_rate, 0) / enabledFeatures.length) * 100
    : 0;
  const afterFeatures = afterSampling * (avgFeatureRate / 100);

  return {
    totalEvents: baseEvents,
    afterFilters: Math.round(afterFilters),
    afterSampling: Math.round(afterSampling),
    afterFeatures: Math.round(afterFeatures),
    finalSent: Math.round(afterFeatures),
    filterDropRate: Math.round(filterDropRate),
    samplingDropRate: Math.round(100 - samplingRate),
    featureDropRate: Math.round(100 - avgFeatureRate),
    totalSentRate: Math.round((afterFeatures / baseEvents) * 100),
  };
};

// UI text constants
export const UI_CONSTANTS = {
  PAGE_TITLE: 'SDK Configuration',
  PAGE_SUBTITLE: 'Control what data your app sends to Pulse',
  
  SECTIONS: {
    FILTERS: {
      TITLE: 'Event Filters',
      DESCRIPTION: 'Block or allow events based on name, properties, scope, and SDK',
    },
    SAMPLING: {
      TITLE: 'Sampling Configuration',
      DESCRIPTION: 'Control what percentage of sessions are sampled',
    },
    SAMPLING_RULES: {
      TITLE: 'Sampling Rules',
      DESCRIPTION: 'Apply different sample rates based on app version',
    },
    CRITICAL_EVENTS: {
      TITLE: 'Critical Events',
      DESCRIPTION: 'Events that are always sent regardless of sampling',
    },
    FEATURES: {
      TITLE: 'Feature Configuration',
      DESCRIPTION: 'Enable/disable features with individual sample rates',
    },
    SIGNALS: {
      TITLE: 'Signals Configuration',
      DESCRIPTION: 'Infrastructure settings for telemetry collection (read-only)',
    },
    INTERACTION: {
      TITLE: 'Interaction Configuration',
      DESCRIPTION: 'Infrastructure settings for interaction tracking (read-only)',
    },
  },
  
  ACTIONS: {
    SAVE: 'Save Configuration',
    RESET: 'Reset to Defaults',
    SAVING: 'Saving...',
    VIEW_JSON: 'View JSON',
  },
  
  NOTIFICATIONS: {
    SAVE_SUCCESS: 'Configuration saved successfully',
    SAVE_ERROR: 'Failed to save configuration',
    LOAD_ERROR: 'Failed to load configuration',
    RESET_SUCCESS: 'Configuration reset to defaults',
  },
};

// Feature display info
export const FEATURE_DISPLAY_INFO: Record<string, { label: string; description: string; icon: string }> = {
  crash_reporting: {
    label: 'Crash Reporting',
    description: 'Capture and report application crashes',
    icon: 'bug',
  },
  network_monitoring: {
    label: 'Network Monitoring',
    description: 'Track API calls, response times, and errors',
    icon: 'network',
  },
  performance_monitoring: {
    label: 'Performance Monitoring',
    description: 'Measure app startup, screen loads, and frame rates',
    icon: 'gauge',
  },
  user_interaction_tracking: {
    label: 'User Interactions',
    description: 'Track taps, scrolls, and navigation patterns',
    icon: 'click',
  },
};
