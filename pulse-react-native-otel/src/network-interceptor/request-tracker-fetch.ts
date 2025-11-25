import type {
  RequestStartContext,
  RequestEndContext,
} from './network.interface';
import { RequestTracker } from './request-tracker';
import { getAbsoluteUrl } from '../utility';
import { createNetworkSpan, completeNetworkSpan } from './span-helpers';

interface GlobalWithFetch {
  fetch: typeof fetch;
}

function createStartContext(
  input: unknown,
  init?: unknown
): RequestStartContext {
  const inputIsRequest = isRequest(input);
  const url = inputIsRequest ? input.url : String(input);
  const method =
    (!!init && (init as RequestInit).method) ||
    (inputIsRequest && input.method) ||
    'GET';
  return { url: getAbsoluteUrl(url), method, type: 'fetch' };
}

function isRequest(input: unknown): input is Request {
  return !!input && typeof input === 'object' && !(input instanceof URL);
}

let isFetchIntercepted = false;
let originalFetch: typeof fetch | null = null;

function createFetchRequestTracker(global: GlobalWithFetch): RequestTracker {
  if (isFetchIntercepted) {
    console.warn('[Pulse] Fetch already intercepted');
    return new RequestTracker();
  }

  if (originalFetch && global.fetch !== originalFetch) {
    console.warn('[Pulse] Fetch already wrapped by another interceptor');
    isFetchIntercepted = true;
    return new RequestTracker();
  }

  if ((global.fetch as any)?._pulseIntercepted) {
    console.warn('[Pulse] Fetch already wrapped by Pulse');
    isFetchIntercepted = true;
    return new RequestTracker();
  }

  const requestTracker = new RequestTracker();
  originalFetch = global.fetch;
  isFetchIntercepted = true;

  const fetchWrapper = function fetch(input: unknown, init?: unknown) {
    const startContext = createStartContext(input, init);

    const span = createNetworkSpan(startContext, 'fetch');
    const { onRequestEnd } = requestTracker.start(startContext);

    const fetchToCall = originalFetch!;
    return fetchToCall
      .call(global, input as RequestInfo, init as RequestInit)
      .then((response) => {
        // Determine if response is successful based on status code
        // 2xx and 3xx are successful, 4xx and 5xx are errors
        const isSuccess = response.status >= 200 && response.status < 400;

        const endContext: RequestEndContext = {
          status: response.status,
          state: isSuccess ? 'success' : 'error',
        };

        completeNetworkSpan(span, startContext, endContext, !isSuccess);
        onRequestEnd(endContext);
        return response;
      })
      .catch((error) => {
        const endContext: RequestEndContext = {
          error,
          state: 'error',
        };

        completeNetworkSpan(span, startContext, endContext, true);
        onRequestEnd(endContext);
        throw error;
      });
  };

  (fetchWrapper as any)._pulseIntercepted = true;
  global.fetch = fetchWrapper;

  return requestTracker;
}

export default createFetchRequestTracker;
