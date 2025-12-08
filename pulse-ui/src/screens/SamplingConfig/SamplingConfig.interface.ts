/**
 * Sampling Configuration Types
 * 
 * These types define the structure for managing client-side data sampling,
 * filtering, and feature configurations.
 */

// SDK types supported by the system
export type SDKType = 'ANDROID' | 'IOS' | 'REACT_NATIVE' | 'WEB';

// Scope types for data collection
export type ScopeType = 'LOGS' | 'TRACES' | 'METRICS';

// Filter mode - determines whether to use whitelist or blacklist
export type FilterMode = 'WHITELIST' | 'BLACKLIST';

// Property filter for events
export interface PropertyFilter {
  name: string;
  value: string; // Regex pattern supported
}

// Filter rule for whitelisting/blacklisting events
export interface FilterRule {
  id?: string;
  name: string;
  props: PropertyFilter[];
  scope: ScopeType[];
  sdks: SDKType[];
}

// Filters configuration section
export interface FiltersConfig {
  mode: FilterMode;
  whitelist: FilterRule[];
  blacklist: FilterRule[];
}

// Match types for sampling rules
export type MatchType = 
  | 'APP_VERSION_MIN' 
  | 'APP_VERSION_MAX' 
  | 'APP_VERSION_EXACT'
  | 'OS_VERSION_MIN'
  | 'OS_VERSION_MAX'
  | 'DEVICE_MODEL'
  | 'COUNTRY'
  | 'USER_SEGMENT';

// Sampling rule match condition
export interface SamplingMatch {
  type: MatchType;
  sdks: SDKType[];
  value: string;
}

// Individual sampling rule
export interface SamplingRule {
  id?: string;
  name: string;
  match: SamplingMatch;
  session_sample_rate: number;
}

// Critical event policy
export interface CriticalEventPolicy {
  id?: string;
  name: string;
  props: PropertyFilter[];
  scope: ScopeType[];
}

// Critical event policies configuration
export interface CriticalEventPolicies {
  alwaysSend: CriticalEventPolicy[];
}

// Default sampling configuration
export interface DefaultSamplingConfig {
  session_sample_rate: number;
}

// Complete sampling configuration
export interface SamplingConfig {
  default: DefaultSamplingConfig;
  rules: SamplingRule[];
  criticalEventPolicies: CriticalEventPolicies;
}

// Signals/telemetry configuration
export interface SignalsConfig {
  scheduleDurationMs: number;
  collectorUrl: string;
  attributesToDrop: string[];
}

// Interaction tracking configuration
export interface InteractionConfig {
  collectorUrl: string;
  configUrl: string;
  beforeInitQueueSize: number;
}

// Feature-level configuration
export interface FeatureConfig {
  id?: string;
  featureName: string;
  enabled: boolean;
  session_sample_rate: number;
  sdks: SDKType[];
}

// Complete SDK configuration
export interface SDKConfig {
  id?: string;
  name?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  filtersConfig: FiltersConfig;
  samplingConfig: SamplingConfig;
  signalsConfig: SignalsConfig;
  interaction: InteractionConfig;
  featureConfigs: FeatureConfig[];
}

// API response wrapper
export interface SDKConfigResponse {
  data: SDKConfig;
  error?: {
    code: string;
    message: string;
  };
}

// Form state for edit mode
export interface SDKConfigFormState {
  isDirty: boolean;
  isSubmitting: boolean;
  errors: Record<string, string>;
}

// Tab identifiers
export type ConfigTab = 
  | 'overview'
  | 'filters'
  | 'sampling'
  | 'signals'
  | 'interaction'
  | 'features';

// Props for section components
export interface SectionProps {
  config: SDKConfig;
  onUpdate: (updates: Partial<SDKConfig>) => void;
  isReadOnly?: boolean;
}

// Common SDK options for multi-select
export const SDK_OPTIONS: { value: SDKType; label: string }[] = [
  { value: 'ANDROID', label: 'Android' },
  { value: 'IOS', label: 'iOS' },
  { value: 'REACT_NATIVE', label: 'React Native' },
  { value: 'WEB', label: 'Web' },
];

// Scope options for multi-select
export const SCOPE_OPTIONS: { value: ScopeType; label: string }[] = [
  { value: 'LOGS', label: 'Logs' },
  { value: 'TRACES', label: 'Traces' },
  { value: 'METRICS', label: 'Metrics' },
];

// Match type options
export const MATCH_TYPE_OPTIONS: { value: MatchType; label: string; description: string }[] = [
  { value: 'APP_VERSION_MIN', label: 'App Version (Min)', description: 'Match versions >= specified' },
  { value: 'APP_VERSION_MAX', label: 'App Version (Max)', description: 'Match versions <= specified' },
  { value: 'APP_VERSION_EXACT', label: 'App Version (Exact)', description: 'Match exact version' },
  { value: 'OS_VERSION_MIN', label: 'OS Version (Min)', description: 'Match OS versions >= specified' },
  { value: 'OS_VERSION_MAX', label: 'OS Version (Max)', description: 'Match OS versions <= specified' },
  { value: 'DEVICE_MODEL', label: 'Device Model', description: 'Match by device model' },
  { value: 'COUNTRY', label: 'Country', description: 'Match by user country' },
  { value: 'USER_SEGMENT', label: 'User Segment', description: 'Match by user segment' },
];

// Predefined feature names
export const FEATURE_NAME_OPTIONS = [
  'network_monitoring',
  'crash_reporting',
  'performance_monitoring',
  'user_interaction_tracking',
  'screen_tracking',
  'anr_detection',
  'memory_monitoring',
  'battery_monitoring',
  'custom_events',
];

