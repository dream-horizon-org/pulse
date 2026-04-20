import createXmlHttpRequestTracker from './request-tracker-xhr';
import type { NetworkHeaderConfig } from './headerConfigStore';
import { setHeaderConfig } from './headerConfigStore';

export type { NetworkHeaderConfig } from './headerConfigStore';
import type { NetworkHeaderConfig } from '../config';
import { PulseLogger } from '../PulseLogger';
// Re-export header utilities for convenience (they're in a separate file to avoid dependency issues)
export { normalizeHeaderName, shouldCaptureHeader } from './header-helper';

let isInitialized = false;
let uninstallXmlHttpRequestTracker: (() => void) | null = null;

export function initializeNetworkInterceptor(
  config?: NetworkHeaderConfig
): void {
  if (isInitialized) {
    PulseLogger.warn('Network interceptor already initialized');
    return;
  }

  setHeaderConfig(
    config ?? {
      requestHeaders: [],
      responseHeaders: [],
    }
  );

  PulseLogger.debug('Starting network interceptor initialization...');

  try {
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
  setHeaderConfig({
    requestHeaders: [],
    responseHeaders: [],
  });
  isInitialized = false;
}
