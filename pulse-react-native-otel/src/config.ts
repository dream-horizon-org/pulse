import { setupErrorHandler } from './errorHandler';
import { isSupportedPlatform } from './initialization';
import {
  createReactNavigationIntegration,
  type ReactNavigationIntegration,
} from './reactNavigation';
import { initializeNetworkInterceptor } from './network-interceptor/initialization';

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
  const autoDetectExceptions = options?.autoDetectExceptions ?? true;
  const autoDetectNavigation = options?.autoDetectNavigation ?? true;
  const autoDetectNetwork = options?.autoDetectNetwork ?? true;
  configure({
    autoDetectExceptions,
    autoDetectNavigation,
    autoDetectNetwork,
  });
}

export function createNavigationIntegrationWithConfig(): ReactNavigationIntegration {
  if (!isSupportedPlatform()) {
    return {
      registerNavigationContainer: (_: unknown) => {},
    };
  }
  if (!currentConfig.autoDetectNavigation) {
    console.warn(
      '[Pulse Navigation] auto-detection disabled via Pulse.start; createNavigationIntegration() returning no-op.'
    );
    const noop: ReactNavigationIntegration = {
      registerNavigationContainer: (_: unknown) => {
        console.warn(
          '[Pulse Navigation] auto-detection disabled via Pulse.start; registerNavigationContainer() returning no-op.'
        );
      },
    };
    return noop;
  }
  return createReactNavigationIntegration();
}
