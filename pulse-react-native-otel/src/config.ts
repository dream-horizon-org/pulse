import { setupErrorHandler } from './errorHandler';
import { isSupportedPlatform } from './initialization';
import {
  createReactNavigationIntegration,
  type ReactNavigationIntegration,
  type NavigationIntegrationOptions,
} from './navigation';
import { initializeNetworkInterceptor } from './network-interceptor/initialization';
import PulseReactNativeOtel from './NativePulseReactNativeOtel';
import type { PulseFeatureConfig } from './pulse.interface';
import { PULSE_FEATURE_NAMES } from './pulse.constants';

export type PulseConfig = {
  autoDetectExceptions?: boolean;
  autoDetectNavigation?: boolean;
  autoDetectNetwork?: boolean;
};

const defaultConfig: Required<PulseConfig> = {
  autoDetectExceptions: true,
  autoDetectNavigation: true,
  autoDetectNetwork: true,
};

let currentConfig: PulseConfig = { ...defaultConfig };

// Cache for features from remote SDK config
let cachedFeatures: PulseFeatureConfig;

/**
 * Gets all features from the remote SDK config.
 * @returns Record of feature names to their enabled status, or null if config not available
 */
export function getFeaturesFromRemoteConfig(): PulseFeatureConfig {
  if (cachedFeatures !== undefined) {
    return cachedFeatures;
  }

  cachedFeatures = PulseReactNativeOtel.getAllFeatures();
  return cachedFeatures;
}

function configure(config: PulseConfig): void {
  currentConfig = {
    ...currentConfig,
    ...config,
  };
  setupErrorHandler(currentConfig.autoDetectExceptions ?? true);

  if (currentConfig.autoDetectNetwork) {
    initializeNetworkInterceptor();
  }
}

function resolveFeatureState(
  features: PulseFeatureConfig,
  featureName: string,
  optionValue: boolean
): boolean {
  if (features !== undefined && features !== null)
    return features[featureName] ?? optionValue;
  return optionValue;
}

function resolveNavigationState(
  features: PulseFeatureConfig,
  optionValue: boolean
): boolean {
  if (features !== undefined && features !== null) {
    const hasAny =
      features[PULSE_FEATURE_NAMES.SCREEN_SESSION] === true ||
      features[PULSE_FEATURE_NAMES.RN_SCREEN_LOAD] === true ||
      features[PULSE_FEATURE_NAMES.RN_SCREEN_INTERACTIVE] === true;
    return hasAny ?? optionValue;
  }
  return optionValue;
}

export function start(options?: PulseConfig): void {
  if (!isSupportedPlatform()) return;

  const features = getFeaturesFromRemoteConfig();
  const config: PulseConfig = {
    autoDetectExceptions: resolveFeatureState(
      features,
      PULSE_FEATURE_NAMES.JS_CRASH,
      options?.autoDetectExceptions ?? defaultConfig.autoDetectExceptions
    ),
    autoDetectNavigation: resolveNavigationState(
      features,
      options?.autoDetectNavigation ?? defaultConfig.autoDetectNavigation
    ),
    autoDetectNetwork: resolveFeatureState(
      features,
      PULSE_FEATURE_NAMES.NETWORK_INSTRUMENTATION,
      options?.autoDetectNetwork ?? defaultConfig.autoDetectNetwork
    ),
  };

  configure(config);
}

export function createNavigationIntegrationWithConfig(
  options?: NavigationIntegrationOptions
): ReactNavigationIntegration {
  if (!isSupportedPlatform()) {
    return {
      registerNavigationContainer: (_: unknown) => () => {},
      markContentReady: () => {},
    };
  }
  if (!currentConfig.autoDetectNavigation) {
    console.warn(
      '[Pulse Navigation] auto-detection disabled via Pulse.start; createNavigationIntegration() returning no-op.'
    );
    const noop: ReactNavigationIntegration = {
      registerNavigationContainer: (_: unknown) => () => {
        console.warn(
          '[Pulse Navigation] auto-detection disabled via Pulse.start; registerNavigationContainer() returning no-op.'
        );
      },
      markContentReady: () => {},
    };
    return noop;
  }
  return createReactNavigationIntegration(options);
}
