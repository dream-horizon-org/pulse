/**
 * Sampling Configuration Constants
 */

import { SDKConfig } from './SamplingConfig.interface';

export const SAMPLING_CONFIG_CONSTANTS = {
  PAGE_TITLE: 'SDK Configuration',
  PAGE_SUBTITLE: 'Manage data sampling, filtering, and feature configurations for your mobile SDKs',
  
  // Tab labels
  TABS: {
    OVERVIEW: 'Overview',
    FILTERS: 'Event Filters',
    SAMPLING: 'Sampling Rules',
    SIGNALS: 'Signals',
    INTERACTION: 'Interaction',
    FEATURES: 'Features',
  },
  
  // Section descriptions
  DESCRIPTIONS: {
    FILTERS: 'Control which events are sent to Pulse by configuring whitelist or blacklist rules.',
    SAMPLING: 'Define sampling rates to control data volume while maintaining statistical significance.',
    SIGNALS: 'Configure telemetry collection intervals and sensitive data handling.',
    INTERACTION: 'Configure interaction tracking endpoints and queue settings.',
    FEATURES: 'Enable or disable specific features and configure their individual sampling rates.',
  },
  
  // Button labels
  BUTTONS: {
    SAVE: 'Save Configuration',
    CANCEL: 'Cancel',
    ADD_RULE: 'Add Rule',
    ADD_POLICY: 'Add Policy',
    ADD_FEATURE: 'Add Feature',
    REMOVE: 'Remove',
    EDIT: 'Edit',
    DUPLICATE: 'Duplicate',
  },
  
  // Notification messages
  NOTIFICATIONS: {
    SAVE_SUCCESS: 'Configuration saved successfully',
    SAVE_ERROR: 'Failed to save configuration',
    VALIDATION_ERROR: 'Please fix validation errors before saving',
    LOAD_ERROR: 'Failed to load configuration',
  },
  
  // Validation messages
  VALIDATION: {
    REQUIRED: 'This field is required',
    INVALID_RATE: 'Sampling rate must be between 0 and 1',
    INVALID_URL: 'Please enter a valid URL',
    INVALID_REGEX: 'Invalid regex pattern',
    DUPLICATE_NAME: 'A rule with this name already exists',
  },
  
  // Default values
  DEFAULTS: {
    SAMPLE_RATE: 0.5,
    SCHEDULE_DURATION: 5000,
    QUEUE_SIZE: 100,
  },
};

// Default/empty configuration
export const DEFAULT_SDK_CONFIG: SDKConfig = {
  filtersConfig: {
    mode: 'BLACKLIST',
    whitelist: [],
    blacklist: [],
  },
  samplingConfig: {
    default: {
      session_sample_rate: 0.5,
    },
    rules: [],
    criticalEventPolicies: {
      alwaysSend: [],
    },
  },
  signalsConfig: {
    scheduleDurationMs: 5000,
    collectorUrl: '',
    attributesToDrop: [],
  },
  interaction: {
    collectorUrl: '',
    configUrl: '',
    beforeInitQueueSize: 100,
  },
  featureConfigs: [],
};

// Feature icons mapping
export const FEATURE_ICONS: Record<string, string> = {
  network_monitoring: 'IconNetwork',
  crash_reporting: 'IconBug',
  performance_monitoring: 'IconChartLine',
  user_interaction_tracking: 'IconClick',
  screen_tracking: 'IconDeviceDesktop',
  anr_detection: 'IconAlertTriangle',
  memory_monitoring: 'IconCpu',
  battery_monitoring: 'IconBattery',
  custom_events: 'IconSparkles',
};

// SDK colors for visual distinction
export const SDK_COLORS: Record<string, string> = {
  ANDROID: '#3DDC84',
  IOS: '#007AFF',
  REACT_NATIVE: '#61DAFB',
  WEB: '#FF6B6B',
};

// Scope colors
export const SCOPE_COLORS: Record<string, string> = {
  LOGS: '#10B981',
  TRACES: '#8B5CF6',
  METRICS: '#F59E0B',
};

