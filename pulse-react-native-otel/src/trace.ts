import PulseReactNativeOtel from './NativePulseReactNativeOtel';
import { isSupportedPlatform } from './initialization';
import { mergeWithGlobalAttributes } from './globalAttributes';
import { extractErrorDetails } from './utility';
import type { PulseAttributes } from './pulse.interface';

export type SpanOptions = {
  attributes?: PulseAttributes;
  inheritContext?: boolean; // If true (default), span will be parented in existing context. If false, creates new context.
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
  if (!isSupportedPlatform()) {
    return {
      end: (_statusCode?: SpanStatusCode) => {},
      addEvent: (_eventName: string, _eventAttributes?: PulseAttributes) => {},
      setAttributes: (_attributes?: PulseAttributes) => {},
      recordException: (_error: Error, _attributes?: PulseAttributes) => {},
      spanId: undefined,
    };
  }

  const mergedAttributes = mergeWithGlobalAttributes(options?.attributes || {});
  const inheritContext = options?.inheritContext ?? true;
  const spanId = PulseReactNativeOtel.startSpan(
    name,
    mergedAttributes,
    inheritContext
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
  if (!isSupportedPlatform()) {
    return fn();
  }

  const mergedAttributes = mergeWithGlobalAttributes(options?.attributes || {});
  const inheritContext = options?.inheritContext ?? true;
  const spanId = PulseReactNativeOtel.startSpan(
    name,
    mergedAttributes,
    inheritContext
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
  PulseReactNativeOtel.endSpan(spanId, statusCode);
}

export function discardSpan(spanId: string): void {
  PulseReactNativeOtel.discardSpan(spanId);
}

function addSpanEvent(
  spanId: string,
  name: string,
  attributes?: PulseAttributes
): void {
  PulseReactNativeOtel.addSpanEvent(spanId, name, attributes || undefined);
}

function setSpanAttributes(spanId: string, attributes?: PulseAttributes): void {
  PulseReactNativeOtel.setSpanAttributes(spanId, attributes || undefined);
}

function recordSpanException(error: Error, attributes?: PulseAttributes): void {
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
