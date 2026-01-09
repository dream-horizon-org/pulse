/**
 * SDK Configuration Interfaces
 * Aligned with Backend PulseConfig Schema
 */

// ============================================================================
// ENUMS - Must match backend enums exactly
// ============================================================================

// SDK platforms - matches backend Sdk enum
export type SdkEnum = 'android_java' | 'android_rn' | 'ios_native' | 'ios_rn';

// Telemetry scopes - matches backend Scope enum
export type ScopeEnum = 'logs' | 'traces' | 'metrics' | 'baggage';

// Filter mode - matches backend FilterMode enum
export type FilterMode = 'blacklist' | 'whitelist';

// Sampling rule names - matches backend rules enum
export type SamplingRuleName =
  | 'os_version'
  | 'app_version'
  | 'country'
  | 'platform'
  | 'state'
  | 'device'
  | 'network';

// Feature names - matches backend Features enum
export type FeatureName =
  | 'interaction'
  | 'java_crash'
  | 'java_anr'
  | 'network_change'
  | 'network_instrumentation'
  | 'screen_session'
  | 'custom_events';

// ============================================================================
// EVENT FILTER TYPES
// ============================================================================

// Event property match (for filters and critical events)
export interface EventPropMatch {
  name: string;
  value: string; // Regex pattern
}

// Event filter rule - matches backend EventFilter
export interface EventFilter {
  id?: string; // For UI tracking only (stripped before API calls)
  name: string;
  props: EventPropMatch[];
  scopes: ScopeEnum[];
  sdks: SdkEnum[];
}

// Attribute value for adding attributes
export interface AttributeValue {
  name: string;
  value: string;
}

// Attribute to add with condition
export interface AttributeToAdd {
  id?: string; // For UI tracking only
  values: AttributeValue[];
  condition: EventFilter;
}

// ============================================================================
// FILTERS CONFIGURATION - Nested under signals
// ============================================================================

// Filter config - matches backend FilterConfig
export interface FilterConfig {
  mode: FilterMode;
  values: EventFilter[];
}

// ============================================================================
// SAMPLING CONFIGURATION
// ============================================================================

// Default sampling config
export interface DefaultSampling {
  sessionSampleRate: number; // 0.0 - 1.0
}

// Sampling rule - matches backend SamplingRule
export interface SamplingRule {
  id?: string; // For UI tracking only
  name: SamplingRuleName;
  sdks: SdkEnum[];
  value: string; // Regex or value to match
  sessionSampleRate: number; // 0.0 - 1.0
}

// Critical policy rule - matches backend CriticalPolicyRule
export interface CriticalPolicyRule {
  id?: string; // For UI tracking only
  name: string;
  props: EventPropMatch[];
  scopes: ScopeEnum[];
  sdks: SdkEnum[];
}

// Critical event policies container
export interface CriticalEventPolicies {
  alwaysSend: CriticalPolicyRule[];
}

// Critical session policies container
export interface CriticalSessionPolicies {
  alwaysSend: CriticalPolicyRule[];
}

// Sampling configuration - matches backend SamplingConfig
export interface SamplingConfig {
  default: DefaultSampling;
  rules: SamplingRule[];
  criticalEventPolicies: CriticalEventPolicies;
  criticalSessionPolicies: CriticalSessionPolicies;
}

// ============================================================================
// SIGNALS CONFIGURATION
// ============================================================================

// Signals configuration - matches backend SignalsConfig
export interface SignalsConfig {
  filters: FilterConfig;
  scheduleDurationMs: number;
  logsCollectorUrl?: string; // Auto-filled by backend if not provided
  metricCollectorUrl?: string; // Auto-filled by backend if not provided
  spanCollectorUrl?: string; // Auto-filled by backend if not provided
  attributesToDrop: EventFilter[];
  attributesToAdd?: AttributeToAdd[];
}

// ============================================================================
// INTERACTION CONFIGURATION
// ============================================================================

// Interaction configuration - matches backend InteractionConfig
export interface InteractionConfig {
  collectorUrl?: string; // Auto-filled by backend if not provided
  configUrl?: string; // Auto-filled by backend if not provided
  beforeInitQueueSize: number;
}

// ============================================================================
// FEATURE CONFIGURATION
// ============================================================================

// Feature configuration - matches backend FeatureConfig
// Note: sessionSampleRate is 0 (disabled) or 1 (enabled) - UI shows as toggle
export interface FeatureConfig {
  id?: string; // For UI tracking only
  featureName: FeatureName;
  sessionSampleRate: number; // 0 = disabled, 1 = enabled (UI shows as on/off toggle)
  sdks: SdkEnum[];
}

// ============================================================================
// MAIN PULSE CONFIG - Matches backend PulseConfig
// ============================================================================

export interface PulseConfig {
  version?: number; // Set by backend on creation
  description: string;
  sampling: SamplingConfig;
  signals: SignalsConfig;
  interaction: InteractionConfig;
  features: FeatureConfig[];
}

// ============================================================================
// API RESPONSE TYPES
// ============================================================================

// Config details for listing - matches backend AllConfigdetails.Configdetails
export interface ConfigVersion {
  version: number;
  isactive: boolean;
  description: string;
  createdBy: string;
  createdAt: string;
}

// All config details response - matches backend AllConfigdetails
export interface AllConfigDetailsResponse {
  configDetails: ConfigVersion[];
}

// Create config response - matches backend CreateConfigResponse
export interface CreateConfigResponse {
  version: number;
}

// Rules and features response - matches backend RulesAndFeaturesResponse
export interface RulesAndFeaturesResponse {
  rules: string[];
  features: string[];
}

// Scopes and SDKs response - matches backend GetScopeAndSdksResponse
export interface ScopesAndSdksResponse {
  scope: string[];
  sdks: string[];
}

// ============================================================================
// UI-SPECIFIC TYPES
// ============================================================================

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

// Editor mode
export type ConfigEditorMode = 'create' | 'edit' | 'view';

// ============================================================================
// COMPONENT PROPS
// ============================================================================

export interface DataPipelineProps {
  stats: PipelineStats;
  isLoading?: boolean;
}

export interface FiltersConfigProps {
  config: FilterConfig;
  onChange: (config: FilterConfig) => void;
  disabled?: boolean;
}

export interface AttributesToDropProps {
  attributes: EventFilter[];
  onChange: (attributes: EventFilter[]) => void;
  disabled?: boolean;
}

export interface SamplingConfigProps {
  config: SamplingConfig;
  onChange: (config: SamplingConfig) => void;
  disabled?: boolean;
}

export interface CriticalPoliciesProps {
  eventPolicies: CriticalEventPolicies;
  sessionPolicies: CriticalSessionPolicies;
  onEventPoliciesChange: (policies: CriticalEventPolicies) => void;
  onSessionPoliciesChange: (policies: CriticalSessionPolicies) => void;
  disabled?: boolean;
}

export interface SignalsConfigProps {
  config: SignalsConfig;
  onChange?: (config: SignalsConfig) => void;
  readOnly?: boolean;
}

export interface InteractionConfigProps {
  config: InteractionConfig;
  onChange?: (config: InteractionConfig) => void;
  readOnly?: boolean;
}

export interface FeatureConfigsProps {
  configs: FeatureConfig[];
  onChange: (configs: FeatureConfig[]) => void;
  disabled?: boolean;
}

export interface ConfigEditorProps {
  initialConfig?: PulseConfig;
  mode: ConfigEditorMode;
  onSave?: (config: PulseConfig) => void;
  onCancel?: () => void;
  onEdit?: () => void;
  viewingVersion?: number | null;
}

export interface ConfigVersionListProps {
  onViewVersion: (version: number) => void;
  onCreateNew: (baseVersion?: number) => void;
}
