import type {
  RequestStartContext,
  RequestEndContext,
} from './network.interface';
import { RequestTracker } from './request-tracker';
import { getAbsoluteUrl } from '../utility';
import type { Span } from '../index';
import { createNetworkSpan, completeNetworkSpan } from './span-helpers';

interface RequestData {
  method: string;
  url: string;
}

type ReadyStateChangeHandler = (this: XMLHttpRequest, ev: Event) => any;

let isXHRIntercepted = false;

function createXmlHttpRequestTracker(
  xhr: typeof XMLHttpRequest
): RequestTracker {
  if (isXHRIntercepted) {
    console.warn('[Pulse] XMLHttpRequest already intercepted');
    return new RequestTracker();
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

  const originalSend = xhr.prototype.send;
  xhr.prototype.send = function send(
    body?: Document | XMLHttpRequestBodyInit | null
  ) {
    const requestData = trackedRequests.get(this);
    if (requestData) {
      const existingHandler = requestHandlers.get(this);
      if (existingHandler)
        this.removeEventListener('readystatechange', existingHandler);

      const startContext: RequestStartContext = {
        type: 'xmlhttprequest',
        method: requestData.method,
        url: requestData.url,
      };

      this.setRequestHeader('X-Pulse-RN-Tracked', 'true');

      const span = createNetworkSpan(startContext, 'xmlhttprequest', body);
      trackedSpans.set(this, span);
      const { onRequestEnd } = requestTracker.start(startContext);

      const onReadyStateChange: ReadyStateChangeHandler = () => {
        if (this.readyState === xhr.DONE && onRequestEnd) {
          const activeSpan = trackedSpans.get(this);

          // Determine request outcome based on status code
          let endContext: RequestEndContext;

          if (this.status <= 0 || this.status >= 400) {
            endContext = { state: 'error', status: this.status };
          } else {
            endContext = { state: 'success', status: this.status };
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

          onRequestEnd(endContext);
        }
      };

      this.addEventListener('readystatechange', onReadyStateChange);
      requestHandlers.set(this, onReadyStateChange);
    }

    originalSend.call(this, body);
  };

  return requestTracker;
}

export default createXmlHttpRequestTracker;
