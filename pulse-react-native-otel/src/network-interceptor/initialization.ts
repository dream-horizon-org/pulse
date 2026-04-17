import createXmlHttpRequestTracker from './request-tracker-xhr';
import type { NetworkHeaderConfig } from '../config';
import { PulseLogger } from '../PulseLogger';
// Re-export header utilities for convenience (they're in a separate file to avoid dependency issues)
export { normalizeHeaderName, shouldCaptureHeader } from './header-helper';

let isInitialized = false;
let uninstallXmlHttpRequestTracker: (() => void) | null = null;
let headerConfig: NetworkHeaderConfig = {
  requestHeaders: [],
  responseHeaders: [],
};

export function getHeaderConfig(): NetworkHeaderConfig {
  return headerConfig;
}

export function initializeNetworkInterceptor(
  config?: NetworkHeaderConfig
): void {
  if (isInitialized) {
    PulseLogger.warn('Network interceptor already initialized');
    return;
  }

  // Store header configuration
  if (config) {
    headerConfig = {
      requestHeaders: config.requestHeaders ?? [],
      responseHeaders: config.responseHeaders ?? [],
    };
  }

  PulseLogger.debug('Starting network interceptor initialization...');

  try {
    // In react-native, we are intercepting XMLHttpRequest only, since axios and fetch both use it internally.
    // See: https://github.com/facebook/react-native/blob/main/packages/react-native/Libraries/Network/fetch.js
    if (typeof XMLHttpRequest !== 'undefined') {
      const result = createXmlHttpRequestTracker(XMLHttpRequest);
      uninstallXmlHttpRequestTracker = result.uninstall;
    } else {
      PulseLogger.warn('XMLHttpRequest is not available');
    }

    isInitialized = true;
  } catch (error) {
    PulseLogger.error(`Failed to initialize network interceptor: ${error}`);
  }
}

export const isNetworkInterceptorInitialized = (): boolean => isInitialized;

export function uninstallNetworkInterceptor(): void {
  if (!isInitialized) {
    return;
  }
  if (uninstallXmlHttpRequestTracker) {
    uninstallXmlHttpRequestTracker();
    uninstallXmlHttpRequestTracker = null;
  }
  isInitialized = false;
}
