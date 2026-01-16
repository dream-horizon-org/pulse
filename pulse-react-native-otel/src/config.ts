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

export type PulseConfig = {
  autoDetectExceptions?: boolean;
  autoDetectNavigation?: boolean;
  autoDetectNetwork?: boolean;
};

export type PulseStartOptions = {
  autoDetectExceptions?: boolean;
  autoDetectNavigation?: boolean;
  autoDetectNetwork?: boolean;
};

const defaultConfig: PulseConfig = {
  autoDetectExceptions: true,
  autoDetectNavigation: true,
  autoDetectNetwork: true,
};

let currentConfig: PulseConfig = { ...defaultConfig };

// Cache for features from remote SDK config
let cachedFeatures: PulseFeatureConfig = null;

/**
 * Gets all features from the remote SDK config.
 * @returns Record of feature names to their enabled status, or null if config not available
 */
export function getFeaturesFromRemoteConfig(): PulseFeatureConfig {
  if (cachedFeatures !== null) {
    return cachedFeatures;
  }

  const features = PulseReactNativeOtel.getAllFeatures();
  cachedFeatures = features;
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
  features: PulseFeatureConfig | null,
  featureName: keyof NonNullable<PulseFeatureConfig>,
  optionValue: boolean | undefined,
  defaultValue: boolean
): boolean {
  if (features === null) return optionValue ?? defaultValue;
  if (features[featureName] === true) return true;
  return optionValue ?? false;
}

function resolveNavigationState(
  features: PulseFeatureConfig | null,
  optionValue: boolean | undefined,
  defaultValue: boolean
): boolean {
  if (features === null) return optionValue ?? defaultValue;
  const hasAny =
    features.screen_session === true ||
    features.rn_screen_load === true ||
    features.rn_screen_interactive === true;
  if (hasAny) return true;
  return optionValue ?? false;
}

export function start(options?: PulseStartOptions): void {
  if (!isSupportedPlatform()) return;

  const features = getFeaturesFromRemoteConfig();
  const config: PulseConfig = {
    autoDetectExceptions: resolveFeatureState(
      features,
      'js_crash',
      options?.autoDetectExceptions,
      true
    ),
    autoDetectNavigation: resolveNavigationState(
      features,
      options?.autoDetectNavigation,
      true
    ),
    autoDetectNetwork: resolveFeatureState(
      features,
      'network_instrumentation',
      options?.autoDetectNetwork,
      true
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
