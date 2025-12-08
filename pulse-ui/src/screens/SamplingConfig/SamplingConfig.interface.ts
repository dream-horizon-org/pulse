/**
 * SDK Configuration Interfaces
 * Matches the PulseConfig JSON Schema
 */

// SDK platforms
export type SdkEnum = 'android_native' | 'android_rn' | 'ios_native' | 'ios_rn';

// Telemetry scopes
export type ScopeEnum = 'logs' | 'traces' | 'metrics' | 'baggage';

// Filter mode
export type FilterMode = 'blacklist' | 'whitelist';

// Sampling match types
export type SamplingMatchType = 'app_version_min' | 'app_version_max';

// Event property match (for filters and critical events)
export interface EventPropMatch {
  name: string;
  value: string; // Regex pattern
}

// Event filter rule
export interface EventFilter {
  id?: string; // For UI tracking only
  name: string;
  props: EventPropMatch[];
  scope: ScopeEnum[];
  sdks: SdkEnum[];
}

// Filters configuration
export interface FiltersConfig {
  mode: FilterMode;
  whitelist: EventFilter[];
  blacklist: EventFilter[];
}

// Sampling match condition
export interface SamplingMatchCondition {
  type: SamplingMatchType;
  sdks: SdkEnum[];
  app_version_min_inclusive?: string; // Required if type is app_version_min
  app_version_max_inclusive?: string; // Required if type is app_version_max
}

// Sampling rule
export interface SamplingRule {
  id?: string; // For UI tracking only
  name: string;
  match: SamplingMatchCondition;
  session_sample_rate: number; // 0.0 - 1.0
}

// Critical event policy
export interface CriticalEventPolicy {
  id?: string; // For UI tracking only
  name: string;
  props: EventPropMatch[];
  scope: ScopeEnum[];
}

// Critical event policies container
export interface CriticalEventPolicies {
  alwaysSend: CriticalEventPolicy[];
}

// Sampling configuration
export interface SamplingConfig {
  default: {
    session_sample_rate: number; // 0.0 - 1.0
  };
  rules: SamplingRule[];
  criticalEventPolicies: CriticalEventPolicies;
}

// Signals configuration
export interface SignalsConfig {
  scheduleDurationMs: number;
  collectorUrl: string;
  attributesToDrop: string[];
}

// Interaction configuration
export interface InteractionConfig {
  collectorUrl: string;
  configUrl: string;
  beforeInitQueueSize: number;
}

// Feature configuration
export interface FeatureConfig {
  id?: string; // For UI tracking only
  featureName: string;
  enabled: boolean;
  session_sample_rate: number; // 0.0 - 1.0
  sdks: SdkEnum[];
}

// Complete Pulse Configuration (matches schema)
export interface PulseConfig {
  version: number;
  filtersConfig: FiltersConfig;
  samplingConfig: SamplingConfig;
  signals: SignalsConfig;
  interaction: InteractionConfig;
  featureConfigs: FeatureConfig[];
}

// Version metadata for listing
export interface ConfigVersion {
  version: number;
  createdAt: string;
  createdBy: string;
  description?: string;
  isActive: boolean; // Currently deployed version
}

// Version list response
export interface ConfigVersionListResponse {
  versions: ConfigVersion[];
  totalCount: number;
}

// Full config with version metadata
export interface PulseConfigWithMeta extends PulseConfig {
  createdAt?: string;
  createdBy?: string;
  description?: string;
  isActive?: boolean;
}

// UI-specific state (extends PulseConfig with UI metadata)
export interface PulseConfigState extends PulseConfig {
  _ui?: {
    isDirty: boolean;
    lastSaved?: string;
  };
}

// Pipeline stats for visualization
export interface PipelineStats {
  totalEvents: number;
  afterFilters: number;
  afterSampling: number;
  afterFeatures: number;
  finalSent: number;
  filterDropRate: number;
  samplingDropRate: number;
  featureDropRate: number;
  totalSentRate: number;
}

// Component props
export interface DataPipelineProps {
  stats: PipelineStats;
  isLoading?: boolean;
}

export interface FiltersConfigProps {
  config: FiltersConfig;
  onChange: (config: FiltersConfig) => void;
}

export interface SamplingConfigProps {
  config: SamplingConfig;
  onChange: (config: SamplingConfig) => void;
}

export interface SignalsConfigProps {
  config: SignalsConfig;
  readOnly?: boolean;
}

export interface InteractionConfigProps {
  config: InteractionConfig;
  readOnly?: boolean;
}

export interface FeatureConfigsProps {
  configs: FeatureConfig[];
  onChange: (configs: FeatureConfig[]) => void;
}

// Editor mode
export type ConfigEditorMode = 'create' | 'edit' | 'view';

// Editor props
export interface ConfigEditorProps {
  initialConfig?: PulseConfig;
  mode: ConfigEditorMode;
  onSave?: (config: PulseConfig) => void;
  onCancel?: () => void;
}
