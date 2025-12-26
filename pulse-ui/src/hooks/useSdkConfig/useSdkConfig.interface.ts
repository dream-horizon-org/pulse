import {
  PulseConfig,
  AllConfigDetailsResponse,
  ConfigVersion,
  CreateConfigResponse,
  RulesAndFeaturesResponse,
  ScopesAndSdksResponse,
} from '../../screens/SamplingConfig/SamplingConfig.interface';

// ============================================================================
// Query Parameters
// ============================================================================

export interface GetSdkConfigByVersionParams {
  version: number | null;
  enabled?: boolean;
}

export interface GetActiveSdkConfigParams {
  enabled?: boolean;
}

export interface GetAllSdkConfigsParams {
  enabled?: boolean;
}

// ============================================================================
// Mutation Parameters
// ============================================================================

export interface CreateSdkConfigInput {
  config: PulseConfig;
}

// ============================================================================
// Callbacks
// ============================================================================

export type OnCreateSettled = (
  data: CreateConfigResponse | undefined,
  error: unknown,
) => void;

// ============================================================================
// Re-export types for convenience
// ============================================================================

export type {
  PulseConfig,
  AllConfigDetailsResponse,
  ConfigVersion,
  CreateConfigResponse,
  RulesAndFeaturesResponse,
  ScopesAndSdksResponse,
};

