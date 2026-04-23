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
import { PulseLogLevel } from './PulseLogLevel';
import { PulseLogger } from './PulseLogger';

export { PulseDataCollectionConsent };
export type { NetworkHeaderConfig } from './network-interceptor/headerConfigStore';
export { getIsShutdown, getIsStarted } from './sessionState';
export { getFeaturesFromRemoteConfig } from './remoteFeatures';
export { PulseLogLevel };

export type PulseConfig = {
  autoDetectExceptions?: boolean;
  autoDetectNavigation?: boolean;
  autoDetectNetwork?: boolean;
  networkHeaders?: NetworkHeaderConfig;
  logLevel?: PulseLogLevel;
};

const defaultConfig: Required<PulseConfig> = {
  autoDetectExceptions: true,
  autoDetectNavigation: true,
  autoDetectNetwork: true,
  networkHeaders: {
    requestHeaders: [],
    responseHeaders: [],
  },
  logLevel: PulseLogLevel.NONE,
};

let currentConfig: PulseConfig = { ...defaultConfig };

/** Last JS-side consent passed to native (for diagnostic logs only). */
let lastDataCollectionConsent: PulseDataCollectionConsent | null = null;

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
      features[PULSE_FEATURE_NAMES.RN_SCREEN_SESSION] === true ||
      features[PULSE_FEATURE_NAMES.RN_SCREEN_LOAD] === true ||
      features[PULSE_FEATURE_NAMES.RN_SCREEN_INTERACTIVE] === true;
    return hasAny ?? optionValue;
  }
  return optionValue;
}

export function start(options?: PulseConfig): void {
  if (!isSupportedPlatform()) return;
  if (getIsShutdown()) {
    PulseLogger.warn(
      'SDK has been shut down. Pulse.start() is a no-op; re-initialization is not supported.'
    );
    return;
  }
  if (getIsStarted()) {
    PulseLogger.debug('sdk.init skipped reason=already_started');
    return;
  }

  const startedAtMs =
    typeof globalThis !== 'undefined' &&
    globalThis.performance != null &&
    typeof globalThis.performance.now === 'function'
      ? globalThis.performance.now()
      : Date.now();

  markPulseSessionStarted();
  PulseLogger.setLevel(options?.logLevel ?? PulseLogLevel.NONE);
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
      PULSE_FEATURE_NAMES.RN_NETWORK,
      options?.autoDetectNetwork ?? defaultConfig.autoDetectNetwork
    ),
    networkHeaders: options?.networkHeaders ?? {
      requestHeaders: [],
      responseHeaders: [],
    },
  };

  configure(config);

  const endedAtMs =
    typeof globalThis !== 'undefined' &&
    globalThis.performance != null &&
    typeof globalThis.performance.now === 'function'
      ? globalThis.performance.now()
      : Date.now();
  const durationMs = Math.round(endedAtMs - startedAtMs);
  const featuresEnabled =
    features != null
      ? Object.entries(features)
          .filter(([, v]) => v === true)
          .map(([k]) => k)
          .join(',')
      : '';
  PulseLogger.info(
    `sdk.init success=true duration_ms=${durationMs} sdk_version=react-native features_enabled=${featuresEnabled}`
  );
}

export function shutdown(): void {
  if (getIsShutdown()) {
    PulseLogger.warn('SDK already shut down.');
    return;
  }
  uninstallErrorHandler();
  uninstallNetworkInterceptor();
  uninstallNavigationIntegration();
  PulseReactNativeOtel.shutdown();
  markPulseSessionShutdown();
  PulseLogger.info('sdk.shutdown graceful=true');
}

/**
 * Updates the data collection consent state.
 */
export function setDataCollectionState(
  state: PulseDataCollectionConsent
): void {
  if (!isSupportedPlatform()) return;
  const from = lastDataCollectionConsent ?? 'unset';
  PulseLogger.info(`sdk.consent.changed from=${from} to=${state}`);
  lastDataCollectionConsent = state;
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
    PulseLogger.warn(
      'Navigation auto-detection disabled via Pulse.start; createNavigationIntegration() returning no-op.'
    );
    const noop: ReactNavigationIntegration = {
      registerNavigationContainer: (_: unknown) => () => {
        PulseLogger.warn(
          'Navigation auto-detection disabled via Pulse.start; registerNavigationContainer() returning no-op.'
        );
      },
      markContentReady: () => {},
    };
    return noop;
  }
  return createReactNavigationIntegration(options);
}
