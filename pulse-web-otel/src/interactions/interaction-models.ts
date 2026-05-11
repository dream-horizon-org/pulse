export type PropertyOperator =
  | "EQUALS"
  | "NOTEQUALS"
  | "CONTAINS"
  | "NOTCONTAINS"
  | "STARTSWITH"
  | "ENDSWITH";

export interface PropertyFilter {
  name: string;
  value: string;
  operator: PropertyOperator;
}

export interface InteractionEvent {
  name: string;
  isBlacklisted: boolean;
  props?: PropertyFilter[] | null;
}

export interface InteractionConfig {
  id: number;
  name: string;
  description: string;
  events: InteractionEvent[];
  /** Inter-step timeout. */
  thresholdInMs: number;
  uptimeLowerLimitInMs: number;
  uptimeMidLimitInMs: number;
  uptimeUpperLimitInMs: number;
  globalBlacklistedEvents: InteractionEvent[];
}
