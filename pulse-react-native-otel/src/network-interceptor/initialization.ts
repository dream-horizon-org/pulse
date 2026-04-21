import createXmlHttpRequestTracker from './request-tracker-xhr';
import type { NetworkHeaderConfig } from './headerConfigStore';
import { setHeaderConfig } from './headerConfigStore';

export type { NetworkHeaderConfig } from './headerConfigStore';
export { normalizeHeaderName, shouldCaptureHeader } from './header-helper';

let isInitialized = false;
let uninstallXmlHttpRequestTracker: (() => void) | null = null;

export function initializeNetworkInterceptor(
  config?: NetworkHeaderConfig
): void {
  if (isInitialized) {
    console.warn('[Pulse] Network interceptor already initialized');
    return;
  }

  setHeaderConfig(
    config ?? {
      requestHeaders: [],
      responseHeaders: [],
    }
  );

  console.log('[Pulse] 🔄 Starting network interceptor initialization...');

  try {
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
  setHeaderConfig({
    requestHeaders: [],
    responseHeaders: [],
  });
  isInitialized = false;
}
