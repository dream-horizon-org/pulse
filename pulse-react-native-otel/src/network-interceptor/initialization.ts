import createXmlHttpRequestTracker from './request-tracker-xhr';
import type { NetworkHeaderConfig } from '../config';
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
    console.warn('[Pulse] Network interceptor already initialized');
    return;
  }

  // Store header configuration
  if (config) {
    headerConfig = {
      requestHeaders: config.requestHeaders ?? [],
      responseHeaders: config.responseHeaders ?? [],
    };
  }

  console.log('[Pulse] 🔄 Starting network interceptor initialization...');

  try {
    // In react-native, we are intercepting XMLHttpRequest only, since axios and fetch both use it internally.
    // See: https://github.com/facebook/react-native/blob/main/packages/react-native/Libraries/Network/fetch.js
    if (typeof XMLHttpRequest !== 'undefined') {
      const result = createXmlHttpRequestTracker(XMLHttpRequest);
      uninstallXmlHttpRequestTracker = result.uninstall;
    } else {
      console.warn('[Pulse] XMLHttpRequest is not available');
    }

    isInitialized = true;
  } catch (error) {
    console.error('[Pulse] Failed to initialize network interceptor:', error);
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
