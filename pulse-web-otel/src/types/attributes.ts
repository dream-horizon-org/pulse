export type PulseAttributePrimitive = string | number | boolean;

export type PulseAttributeValue =
  | PulseAttributePrimitive
  | string[]
  | number[]
  | boolean[];

export type PulseAttributes = Record<string, PulseAttributeValue>;
