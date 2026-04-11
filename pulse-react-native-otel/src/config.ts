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
import {
  getIsShutdown,
  getIsStarted,
  markPulseSessionStarted,
  markPulseSessionShutdown,
} from './sessionState';
import { getFeaturesFromRemoteConfig } from './remoteFeatures';
import type { NetworkHeaderConfig } from './network-interceptor/headerConfigStore';

export { PulseDataCollectionConsent };
export type { NetworkHeaderConfig } from './network-interceptor/headerConfigStore';
export { getIsShutdown, getIsStarted } from './sessionState';
export { getFeaturesFromRemoteConfig } from './remoteFeatures';

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
  if (getIsShutdown()) {
    console.log(
      '[Pulse] SDK has been shut down. Pulse.start() is a no-op; re-initialization is not supported.'
    );
    return;
  }
  if (getIsStarted()) {
    console.log('[Pulse] SDK already started.');
    return;
  }

  markPulseSessionStarted();
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
  if (getIsShutdown()) {
    console.warn('[Pulse] SDK already shut down.');
    return;
  }
  uninstallErrorHandler();
  uninstallNetworkInterceptor();
  uninstallNavigationIntegration();
  PulseReactNativeOtel.shutdown();
  markPulseSessionShutdown();
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
  if (!getIsStarted()) {
    return {
      registerNavigationContainer: (_: unknown) => () => {},
      markContentReady: () => {},
    };
  }
  if (getIsShutdown()) {
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
