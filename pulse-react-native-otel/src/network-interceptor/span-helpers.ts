import { Platform } from 'react-native';
import type {
  RequestStartContext,
  RequestEndContext,
} from './network.interface';
import type { Span } from '../index';
import { Pulse, SpanStatusCode } from '../index';
import type { PulseAttributes } from '../pulse.interface';
import { extractHttpAttributes } from './url-helper';
import { updateAttributesWithGraphQLData } from './graphql-helper';
import { ATTRIBUTE_KEYS, PHASE_VALUES } from '../pulse.constants';

export function setNetworkSpanAttributes(
  span: Span,
  startContext: RequestStartContext,
  endContext: RequestEndContext
): PulseAttributes {
  const method = startContext.method.toUpperCase();
  let attributes: PulseAttributes = {
    [ATTRIBUTE_KEYS.HTTP_METHOD]: method,
    [ATTRIBUTE_KEYS.HTTP_URL]: startContext.url,
    [ATTRIBUTE_KEYS.PULSE_TYPE]: `network.${endContext.status ?? 0}`,
    [ATTRIBUTE_KEYS.HTTP_REQUEST_TYPE]: startContext.type,
    [ATTRIBUTE_KEYS.PLATFORM]: Platform.OS,
  };

  // We had implemented our own URL parsing helper to avoid errors on RN < 0.80. Since this is not supported by React Native.
  // Check here: https://github.com/facebook/react-native/blob/v0.79.0/packages/react-native/Libraries/Blob/URL.js
  const urlAttributes = extractHttpAttributes(startContext.url);
  attributes = { ...attributes, ...urlAttributes };

  if (endContext.status) {
    attributes[ATTRIBUTE_KEYS.HTTP_STATUS_CODE] = endContext.status;
  }

  if (endContext.state === 'error' && endContext.error) {
    attributes.error = true;
    attributes[ATTRIBUTE_KEYS.ERROR_MESSAGE] =
      endContext.error.message || String(endContext.error);
    if (endContext.error.stack) {
      attributes[ATTRIBUTE_KEYS.ERROR_STACK] = endContext.error.stack;
    }
    span.recordException(endContext.error, attributes);
  }

  span.setAttributes(attributes);
  return attributes;
}

export function createNetworkSpan(
  startContext: RequestStartContext,
  interceptorType: 'fetch' | 'xmlhttprequest',
  body?: Document | XMLHttpRequestBodyInit | null
): Span {
  const method = startContext.method.toUpperCase();
  const spanName = `HTTP ${method}`;

  let baseAttributes: PulseAttributes = {
    [ATTRIBUTE_KEYS.HTTP_METHOD]: method,
    [ATTRIBUTE_KEYS.HTTP_URL]: startContext.url,
    [ATTRIBUTE_KEYS.PULSE_TYPE]: PHASE_VALUES.NETWORK,
    [ATTRIBUTE_KEYS.HTTP_REQUEST_TYPE]: interceptorType,
  };

  const graphqlAttributes = updateAttributesWithGraphQLData(
    startContext.url,
    body
  );
  const attributes = { ...baseAttributes, ...graphqlAttributes };

  const span = Pulse.startSpan(spanName, { attributes });

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
