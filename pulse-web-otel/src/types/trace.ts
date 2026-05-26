import type { PulseAttributes } from "./attributes";

export enum SpanStatusCode {
  OK = "OK",
  ERROR = "ERROR",
  UNSET = "UNSET",
}

export type SpanOptions = {
  attributes?: PulseAttributes;
};

/**
 * Lightweight wrapper around an OTel span returned by {@link Pulse.startSpan}.
 *
 * Web deliberately omits {@code spanId} (present on the RN {@code Span} type) because
 * web wraps the OTel span via closure — there is no native bridge ID to track.
 */
export type PulseSpan = {
  end: (statusCode?: SpanStatusCode) => void;
  addEvent: (name: string, attributes?: PulseAttributes) => void;
  setAttributes: (attributes: PulseAttributes) => void;
  recordException: (error: Error, attributes?: PulseAttributes) => void;
};

export const noopSpan: PulseSpan = {
  end: () => {},
  addEvent: () => {},
  setAttributes: () => {},
  recordException: () => {},
};
