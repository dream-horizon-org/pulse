/**
 * OpenTelemetry attribute value types.
 * Based on OpenTelemetry JavaScript SDK attribute types.
 * @see https://github.com/open-telemetry/opentelemetry-js/blob/main/api/src/common/Attributes.ts
 */
export type PulseAttributeValue =
  | string
  | number
  | boolean
  | string[]
  | number[]
  | boolean[];

export type PulseAttributes = Record<string, PulseAttributeValue | undefined>;
