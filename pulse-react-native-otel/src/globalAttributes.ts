import type { PulseAttributes, PulseAttributeValue } from './pulse.interface';

const globalAttributes: PulseAttributes = {};

export function setGlobalAttribute(
  key: string,
  value: PulseAttributeValue
): void {
  globalAttributes[key] = value;
}

export function mergeWithGlobalAttributes<T extends PulseAttributes>(
  attributes?: T
): T & PulseAttributes {
  return { ...globalAttributes, ...attributes } as T & PulseAttributes;
}
