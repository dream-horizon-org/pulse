import { Platform } from 'react-native';
import type {
  RequestStartContext,
  RequestEndContext,
} from './network.interface';
import type { Span } from '../index';
import { Pulse, SpanStatusCode } from '../index';
import type { PulseAttributes } from '../pulse.interface';

export function setNetworkSpanAttributes(
  span: Span,
  startContext: RequestStartContext,
  endContext: RequestEndContext
): PulseAttributes {
  let urlObj: URL | null = null;
  try {
    urlObj = new URL(startContext.url);
  } catch (e) {
    // URL might be relative or invalid, continue without parsing
  }

  const method = startContext.method.toUpperCase();
  const attributes: PulseAttributes = {
    'http.method': method,
    'http.url': startContext.url,
    'pulse.type': 'network',
    'http.request.type': startContext.type,
    'platform': Platform.OS as 'android' | 'ios' | 'web',
  };

  if (urlObj) {
    attributes['http.scheme'] = urlObj.protocol.replace(':', '');
    attributes['http.host'] = urlObj.hostname;
    attributes['http.target'] = urlObj.pathname + urlObj.search;
    if (urlObj.port) {
      attributes['net.peer.port'] = parseInt(urlObj.port, 10);
    }
    attributes['net.peer.name'] = urlObj.hostname;
  }

  if (endContext.status) {
    attributes['http.status_code'] = endContext.status;
  }

  if (endContext.state === 'error' && endContext.error) {
    attributes.error = true;
    attributes['error.message'] =
      endContext.error.message || String(endContext.error);
    if (endContext.error.stack) {
      attributes['error.stack'] = endContext.error.stack;
    }
    span.recordException(endContext.error, attributes);
  }

  span.setAttributes(attributes);
  return attributes;
}

export function createNetworkSpan(
  startContext: RequestStartContext,
  interceptorType: 'fetch' | 'xmlhttprequest'
): Span {
  const method = startContext.method.toUpperCase();
  const spanName = `HTTP ${method}`;

  const span = Pulse.startSpan(spanName, {
    attributes: {
      'http.method': method,
      'http.url': startContext.url,
      'pulse.type': 'network',
      'http.request.type': interceptorType,
    },
  });

  return span;
}

export function completeNetworkSpan(
  span: Span,
  startContext: RequestStartContext,
  endContext: RequestEndContext,
  isError: boolean
): void {
  try {
    const attributes = setNetworkSpanAttributes(span, startContext, endContext);
    console.log('[Pulse] Network span completed', {
      spanId: span.spanId,
      spanAttributes: attributes,
    });
  } catch (e) {
    console.error('[Pulse] Failed to set span attributes:', e);
  }

  span.end(isError ? SpanStatusCode.ERROR : SpanStatusCode.UNSET);
}
