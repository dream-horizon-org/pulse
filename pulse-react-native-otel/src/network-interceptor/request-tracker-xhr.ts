import type {
  RequestStartContext,
  RequestEndContext,
} from './network.interface';
import { RequestTracker } from './request-tracker';
import { getAbsoluteUrl } from '../utility';
import type { Span } from '../index';
import { createNetworkSpan, completeNetworkSpan } from './span-helpers';
import { getHeaderConfig } from './initialization';
import { shouldCaptureHeader } from './header-helper';

interface RequestData {
  method: string;
  url: string;
}

type ReadyStateChangeHandler = (this: XMLHttpRequest, ev: Event) => any;

let isXHRIntercepted = false;

export interface XmlHttpRequestTrackerResult {
  requestTracker: RequestTracker;
  uninstall: () => void;
}

function createXmlHttpRequestTracker(
  xhr: typeof XMLHttpRequest
): XmlHttpRequestTrackerResult {
  if (isXHRIntercepted) {
    console.warn('[Pulse] XMLHttpRequest already intercepted');
    return { requestTracker: new RequestTracker(), uninstall: () => {} };
  }

  const requestTracker = new RequestTracker();
  const trackedRequests = new WeakMap<XMLHttpRequest, RequestData>();
  const trackedSpans = new WeakMap<XMLHttpRequest, Span>();
  const requestHandlers = new WeakMap<
    XMLHttpRequest,
    ReadyStateChangeHandler
  >();

  const originalOpen = xhr.prototype.open;
  xhr.prototype.open = function open(
    method: string,
    url: string | URL,
    ...rest: any[]
  ): void {
    trackedRequests.set(this, {
      method,
      url: getAbsoluteUrl(String(url)),
    });

    // @ts-expect-error rest
    originalOpen.call(this, method, url, ...rest);
  };
  isXHRIntercepted = true;

  // Store request headers before send
  const requestHeadersMap = new WeakMap<
    XMLHttpRequest,
    Record<string, string>
  >();
  const originalSetRequestHeader = xhr.prototype.setRequestHeader;
  xhr.prototype.setRequestHeader = function setRequestHeader(
    name: string,
    value: string
  ): void {
    const headerConfig = getHeaderConfig();
    const requestHeadersList = headerConfig.requestHeaders ?? [];
    if (
      requestHeadersList.length > 0 &&
      shouldCaptureHeader(name, requestHeadersList)
    ) {
      const existing = requestHeadersMap.get(this) || {};
      requestHeadersMap.set(this, { ...existing, [name]: value });
    }
    originalSetRequestHeader.call(this, name, value);
  };

  const originalSend = xhr.prototype.send;
  xhr.prototype.send = function send(
    body?: Document | XMLHttpRequestBodyInit | null
  ) {
    const requestData = trackedRequests.get(this);
    if (requestData) {
      const existingHandler = requestHandlers.get(this);
      if (existingHandler)
        this.removeEventListener('readystatechange', existingHandler);

      // Capture request headers
      const headerConfig = getHeaderConfig();
      const capturedRequestHeaders = requestHeadersMap.get(this);
      const requestHeadersList = headerConfig.requestHeaders ?? [];
      const filteredRequestHeaders: Record<string, string> | undefined =
        capturedRequestHeaders && requestHeadersList.length > 0
          ? Object.fromEntries(
              Object.entries(capturedRequestHeaders).filter(([name]) =>
                shouldCaptureHeader(name, requestHeadersList)
              )
            )
          : undefined;

      const startContext: RequestStartContext = {
        type: 'xmlhttprequest',
        method: requestData.method,
        url: requestData.url,
        requestHeaders:
          filteredRequestHeaders &&
          Object.keys(filteredRequestHeaders).length > 0
            ? filteredRequestHeaders
            : undefined,
      };

      this.setRequestHeader('X-Pulse-RN-Tracked', 'true');

      const span = createNetworkSpan(startContext, 'xmlhttprequest', body);
      trackedSpans.set(this, span);
      const { onRequestEnd } = requestTracker.start(startContext);

      const onReadyStateChange: ReadyStateChangeHandler = () => {
        if (this.readyState === xhr.DONE && onRequestEnd) {
          const activeSpan = trackedSpans.get(this);

          // Capture response headers
          const responseHeaderConfig = getHeaderConfig();
          const capturedResponseHeaders: Record<string, string> = {};
          const responseHeadersList =
            responseHeaderConfig.responseHeaders ?? [];
          if (responseHeadersList.length > 0) {
            try {
              const allHeaders = this.getAllResponseHeaders();
              if (allHeaders) {
                const headerLines = allHeaders.trim().split(/[\r\n]+/);
                for (const line of headerLines) {
                  const parts = line.split(': ');
                  if (parts.length === 2) {
                    const [name, value] = parts;
                    if (
                      name &&
                      value &&
                      shouldCaptureHeader(name, responseHeadersList)
                    ) {
                      capturedResponseHeaders[name] = value;
                    }
                  }
                }
              }
            } catch (e) {
              // Headers may not be available in some cases (CORS, etc.)
              console.debug('[Pulse] Could not read response headers:', e);
            }
          }

          // Determine request outcome based on status code
          let endContext: RequestEndContext;

          const responseHeaders =
            Object.keys(capturedResponseHeaders).length > 0
              ? capturedResponseHeaders
              : undefined;

          if (this.status <= 0 || this.status >= 400) {
            endContext = {
              state: 'error',
              status: this.status,
              responseHeaders,
            };
          } else {
            endContext = {
              state: 'success',
              status: this.status,
              responseHeaders,
            };
          }

          if (activeSpan) {
            completeNetworkSpan(
              activeSpan,
              startContext,
              endContext,
              endContext.state === 'error'
            );
            trackedSpans.delete(this);
          }

          // Clean up
          requestHeadersMap.delete(this);

          onRequestEnd(endContext);
        }
      };

      this.addEventListener('readystatechange', onReadyStateChange);
      requestHandlers.set(this, onReadyStateChange);
    }

    originalSend.call(this, body);
  };

  const uninstall = (): void => {
    if (!isXHRIntercepted) return;
    xhr.prototype.open = originalOpen;
    xhr.prototype.setRequestHeader = originalSetRequestHeader;
    xhr.prototype.send = originalSend;
    isXHRIntercepted = false;
  };

  return { requestTracker, uninstall };
}

export default createXmlHttpRequestTracker;
