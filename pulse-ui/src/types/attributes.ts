/**
 * Shared attribute value types for telemetry attributes.
 */
export type AttributePrimitive = string | number | boolean | null;

export type AttributeValue =
  | AttributePrimitive
  | AttributePrimitive[]
  | { [key: string]: AttributeValue };
