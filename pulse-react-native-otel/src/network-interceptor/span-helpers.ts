import { Platform } from 'react-native';
import type {
  RequestStartContext,
  RequestEndContext,
} from './network.interface';
import { startSpan, SpanStatusCode, type Span } from '../trace';
import type { PulseAttributes } from '../pulse.interface';
import { extractHttpAttributes } from './url-helper';
import { updateAttributesWithGraphQLData } from './graphql-helper';
import { ATTRIBUTE_KEYS, PULSE_TYPES } from '../pulse.constants';
import { normalizeHeaderName } from './header-helper';
import {
  getHeaderCaseInsensitive,
  parseContentLength,
} from './content-length-parser';
import { PulseLogger } from '../PulseLogger';

export function setNetworkSpanAttributes(
  span: Span,
  startContext: RequestStartContext,
  endContext: RequestEndContext
): PulseAttributes {
  const method = startContext.method.toUpperCase();
  let attributes: PulseAttributes = {
    [ATTRIBUTE_KEYS.HTTP_METHOD]: method,
    [ATTRIBUTE_KEYS.HTTP_URL]: startContext.url,
    [ATTRIBUTE_KEYS.PULSE_TYPE]: `${PULSE_TYPES.NETWORK}.${endContext.status ?? 0}`,
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

  const requestBodyLen =
    startContext.requestBodyContentLength ??
    parseContentLength(
      getHeaderCaseInsensitive(startContext.requestHeaders, 'content-length')
    );
  if (requestBodyLen !== undefined) {
    attributes[ATTRIBUTE_KEYS.HTTP_REQUEST_BODY_SIZE] = requestBodyLen;
  }

  if (endContext.responseBodyContentLength !== undefined) {
    attributes[ATTRIBUTE_KEYS.HTTP_RESPONSE_BODY_SIZE] =
      endContext.responseBodyContentLength;
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

  if (startContext.requestHeaders) {
    for (const [headerName, headerValue] of Object.entries(
      startContext.requestHeaders
    )) {
      const normalizedName = normalizeHeaderName(headerName);
      attributes[`${ATTRIBUTE_KEYS.HTTP_REQUEST_HEADER}.${normalizedName}`] =
        headerValue;
    }
  }

  if (endContext.responseHeaders) {
    for (const [headerName, headerValue] of Object.entries(
      endContext.responseHeaders
    )) {
      const normalizedName = normalizeHeaderName(headerName);
      attributes[`${ATTRIBUTE_KEYS.HTTP_RESPONSE_HEADER}.${normalizedName}`] =
        headerValue;
    }
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
    [ATTRIBUTE_KEYS.HTTP_REQUEST_TYPE]: interceptorType,
  };

  const graphqlAttributes = updateAttributesWithGraphQLData(
    startContext.url,
    body
  );
  const attributes = { ...baseAttributes, ...graphqlAttributes };

  const span = startSpan(spanName, { attributes });

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
    PulseLogger.debug(
      `Network span completed spanId=${span.spanId} spanAttributes=${JSON.stringify(attributes)}`
    );
  } catch (e) {
    PulseLogger.error(`Failed to set span attributes: ${e}`);
  }

  span.end(isError ? SpanStatusCode.ERROR : SpanStatusCode.UNSET);
}
