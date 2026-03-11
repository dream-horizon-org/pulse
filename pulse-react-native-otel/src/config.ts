import { setupErrorHandler, uninstallErrorHandler } from './errorHandler';
import { isSupportedPlatform } from './initialization';
import {
  createReactNavigationIntegration,
  uninstallNavigationIntegration,
  type ReactNavigationIntegration,
  type NavigationIntegrationOptions,
} from './navigation';
import {
  initializeNetworkInterceptor,
  uninstallNetworkInterceptor,
} from './network-interceptor/initialization';
import PulseReactNativeOtel, {
  PulseDataCollectionConsent,
} from './NativePulseReactNativeOtel';
import type { PulseFeatureConfig } from './pulse.interface';
import { PULSE_FEATURE_NAMES } from './pulse.constants';

export { PulseDataCollectionConsent };

export type NetworkHeaderConfig = {
  requestHeaders?: string[];
  responseHeaders?: string[];
};

export type PulseConfig = {
  autoDetectExceptions?: boolean;
  autoDetectNavigation?: boolean;
  autoDetectNetwork?: boolean;
  networkHeaders?: NetworkHeaderConfig;
};

const defaultConfig: Required<PulseConfig> = {
  autoDetectExceptions: true,
  autoDetectNavigation: true,
  autoDetectNetwork: true,
  networkHeaders: {
    requestHeaders: [],
    responseHeaders: [],
  },
};

let currentConfig: PulseConfig = { ...defaultConfig };

/** After shutdown, start() and initialize are no-ops; re-initialization is not supported. */
let isShutdown = false;

/** True only after start() has been called at least once. Integrations (e.g. navigation) are no-ops until then. */
let isStarted = false;

// Cache for features from remote SDK config
let cachedFeatures: PulseFeatureConfig;

export function getIsShutdown(): boolean {
  return isShutdown;
}

/** True only after start() has been called at least once. Public APIs (trackEvent, reportException, startSpan, etc.) no-op until then. */
export function getIsStarted(): boolean {
  return isStarted;
}

/**
 * Gets all features from the remote SDK config.
 * @returns Record of feature names to their enabled status, or null if config not available or start() not called
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
    initializeNetworkInterceptor(
      currentConfig.networkHeaders ?? {
        requestHeaders: [],
        responseHeaders: [],
      }
    );
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
  if (isShutdown) {
    console.log(
      '[Pulse] SDK has been shut down. Pulse.start() is a no-op; re-initialization is not supported.'
    );
    return;
  }
  if (isStarted) {
    console.log('[Pulse] SDK already started.');
    return;
  }

  isStarted = true;
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
    networkHeaders: options?.networkHeaders ?? {
      requestHeaders: [],
      responseHeaders: [],
    },
  };

  configure(config);
}

export function shutdown(): void {
  if (isShutdown) {
    console.warn('[Pulse] SDK already shut down.');
    return;
  }
  uninstallErrorHandler();
  uninstallNetworkInterceptor();
  uninstallNavigationIntegration();
  PulseReactNativeOtel.shutdown();
  isShutdown = true;
}

/**
 * Updates the data collection consent state.
 */
export function setDataCollectionState(
  state: PulseDataCollectionConsent
): void {
  if (!isSupportedPlatform()) return;
  PulseReactNativeOtel.setDataCollectionState(state);
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
  if (!isStarted) {
    return {
      registerNavigationContainer: (_: unknown) => () => {},
      markContentReady: () => {},
    };
  }
  if (isShutdown) {
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
