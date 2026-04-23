export type PropertyOperator =
  | "EQUALS"
  | "NOT_EQUALS"
  | "CONTAINS"
  | "NOT_CONTAINS"
  | "STARTS_WITH"
  | "ENDS_WITH";

export interface PropertyFilter {
  key: string;
  value: string;
  operator: PropertyOperator;
}

export interface InteractionEvent {
  name: string;
  required: boolean;
  isBlacklisted?: boolean;
  props?: PropertyFilter[];
}

export interface InteractionConfig {
  id: string;
  name: string;
  events: InteractionEvent[];
  /** Inter-step timeout. */
  thresholdInMs: number;
  uptimeLowerLimitInMs: number;
  uptimeMidLimitInMs: number;
  uptimeUpperLimitInMs: number;
  globalBlacklistedEvents: string[];
}
