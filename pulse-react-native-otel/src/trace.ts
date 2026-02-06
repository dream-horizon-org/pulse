import PulseReactNativeOtel from './NativePulseReactNativeOtel';
import { getIsShutdown } from './config';
import { isSupportedPlatform } from './initialization';
import { mergeWithGlobalAttributes } from './globalAttributes';
import { extractErrorDetails } from './utility';
import type { PulseAttributes } from './pulse.interface';

const noopSpan: Span = {
  end: (_statusCode?: SpanStatusCode) => {},
  addEvent: (_eventName: string, _eventAttributes?: PulseAttributes) => {},
  setAttributes: (_attributes?: PulseAttributes) => {},
  recordException: (_error: Error, _attributes?: PulseAttributes) => {},
  spanId: undefined,
};

/**
 * Options for starting a span.
 * @param attributes - Attributes to set on the span.
 * @param inheritContext - Controls whether or not the new span will be parented in the existing (current) context. If false, a new context is created.
 */
export type SpanOptions = {
  attributes?: PulseAttributes;
  inheritContext?: boolean;
};

export enum SpanStatusCode {
  OK = 'OK',
  ERROR = 'ERROR',
  UNSET = 'UNSET',
}

export type Span = {
  end: (statusCode?: SpanStatusCode) => void;
  addEvent: (name: string, attributes?: PulseAttributes) => void;
  setAttributes: (attributes?: PulseAttributes) => void;
  recordException: (error: Error, attributes?: PulseAttributes) => void;
  // This is the auto-generated ID for span on the native side.
  spanId?: string;
};

export function startSpan(name: string, options?: SpanOptions): Span {
  if (!isSupportedPlatform() || getIsShutdown()) {
    return noopSpan;
  }

  const mergedAttributes = mergeWithGlobalAttributes(options?.attributes || {});
  const inheritContext = options?.inheritContext ?? true;
  const spanId = PulseReactNativeOtel.startSpan(
    name,
    inheritContext,
    mergedAttributes
  );
  return {
    end: (statusCode?: SpanStatusCode) => {
      return endSpan(spanId, statusCode);
    },
    addEvent: (eventName: string, eventAttributes?: PulseAttributes) => {
      return addSpanEvent(spanId, eventName, eventAttributes);
    },
    setAttributes: (spanAttributes?: PulseAttributes) => {
      return setSpanAttributes(spanId, spanAttributes);
    },
    recordException: (error: Error, attributes?: PulseAttributes) => {
      return recordSpanException(error, attributes);
    },
    spanId: spanId,
  };
}

export function trackSpan<T>(
  name: string,
  options: SpanOptions,
  fn: () => T | Promise<T>
): T | Promise<T> {
  if (!isSupportedPlatform() || getIsShutdown()) {
    return fn();
  }

  const mergedAttributes = mergeWithGlobalAttributes(options?.attributes || {});
  const inheritContext = options?.inheritContext ?? true;
  const spanId = PulseReactNativeOtel.startSpan(
    name,
    inheritContext,
    mergedAttributes
  );

  const result = fn();

  if (result && typeof (result as any).then === 'function') {
    return (result as Promise<T>).finally(() => {
      endSpan(spanId, SpanStatusCode.UNSET);
    });
  }

  endSpan(spanId, SpanStatusCode.UNSET);
  return result as T;
}

function endSpan(spanId: string, statusCode?: SpanStatusCode): void {
  if (getIsShutdown()) return;
  PulseReactNativeOtel.endSpan(spanId, statusCode);
}

export function discardSpan(spanId: string): void {
  if (!isSupportedPlatform() || getIsShutdown()) {
    return;
  }
  PulseReactNativeOtel.discardSpan(spanId);
}

function addSpanEvent(
  spanId: string,
  name: string,
  attributes?: PulseAttributes
): void {
  if (getIsShutdown()) return;
  PulseReactNativeOtel.addSpanEvent(spanId, name, attributes || undefined);
}

function setSpanAttributes(spanId: string, attributes?: PulseAttributes): void {
  if (getIsShutdown()) return;
  PulseReactNativeOtel.setSpanAttributes(spanId, attributes || undefined);
}

function recordSpanException(error: Error, attributes?: PulseAttributes): void {
  if (getIsShutdown()) return;
  const { message, stackTrace, errorType } = extractErrorDetails(error);
  const observedTimeMs = Date.now();
  const mergedAttributes = mergeWithGlobalAttributes(attributes || {});
  PulseReactNativeOtel.reportException(
    message,
    observedTimeMs,
    stackTrace || '',
    false,
    errorType,
    mergedAttributes
  );
}
