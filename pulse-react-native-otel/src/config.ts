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
 * @returns Record of feature names to their enabled status
 */
export function getFeaturesFromRemoteConfig(): PulseFeatureConfig {
  if (cachedFeatures !== null) {
    return cachedFeatures;
  }

  const features = PulseReactNativeOtel.getAllFeatures();
  cachedFeatures = features as PulseFeatureConfig;
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

export function start(options?: PulseStartOptions): void {
  if (!isSupportedPlatform()) {
    return;
  }
  const features = getFeaturesFromRemoteConfig();
  const autoDetectExceptions =
    features?.js_crash ?? options?.autoDetectExceptions ?? true;
  const autoDetectNavigation =
    features?.rn_navigation ?? options?.autoDetectNavigation ?? true;
  const autoDetectNetwork =
    features?.network_instrumentation ?? options?.autoDetectNetwork ?? true;

  configure({
    autoDetectExceptions,
    autoDetectNavigation,
    autoDetectNetwork,
  });
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
