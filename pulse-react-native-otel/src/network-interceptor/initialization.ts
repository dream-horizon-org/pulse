import createXmlHttpRequestTracker from './request-tracker-xhr';

let isInitialized = false;

export function initializeNetworkInterceptor(): void {
  if (isInitialized) {
    console.warn('[Pulse] Network interceptor already initialized');
    return;
  }

  console.log('[Pulse] 🔄 Starting network interceptor initialization...');

  try {
    // In react-native, we are intercepting XMLHttpRequest only, since axios and fetch both use it internally.
    // See: https://github.com/facebook/react-native/blob/main/packages/react-native/Libraries/Network/fetch.js
    if (typeof XMLHttpRequest !== 'undefined') {
      createXmlHttpRequestTracker(XMLHttpRequest);
    } else {
      console.warn('[Pulse] XMLHttpRequest is not available');
    }

    isInitialized = true;
  } catch (error) {
    console.error('[Pulse] Failed to initialize network interceptor:', error);
  }
}

export const isNetworkInterceptorInitialized = (): boolean => isInitialized;
