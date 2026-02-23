import type { AttributeValue } from "../../types/attributes";

export interface GetSpanDetailsParams {
  dataType: "TRACES" | "LOGS" | "EXCEPTIONS";
  traceId: string;
  spanId: string;
  timestamp: string;
  groupId?: string; // Used for exceptions
  enabled?: boolean;
}

export type ExceptionEventName =
  | "crash"
  | "anr"
  | "non_fatal"
  | "unknown"
  | (string & {});

export type PlatformType =
  | "Android"
  | "iOS"
  | "Web"
  | "Unknown"
  | (string & {});

export interface ExceptionStackTraceNode {
  type?: string | null;
  message?: string | null;
  stackTrace?: string | null;
  cause?: ExceptionStackTraceNode | null;
  suppressed?: ExceptionStackTraceNode[];
}

export interface SpanDetailsResponse {
  resourceAttributes: Record<string, AttributeValue>;
  spanAttributes: Record<string, AttributeValue>;
  events: Array<{
    timestamp: string;
    name: string;
    attributes: Record<string, AttributeValue>;
  }>;
  links: Array<{
    traceId: string;
    spanId: string;
    attributes: Record<string, AttributeValue>;
  }>;
}

export interface LogDetailsResponse {
  resourceAttributes: Record<string, AttributeValue>;
  logAttributes: Record<string, AttributeValue>;
  scopeAttributes: Record<string, AttributeValue>;
  body: string;
  severityText: string;
  severityNumber: number;
}

export interface ExceptionDetailsResponse {
  resourceAttributes: Record<string, AttributeValue>;
  logAttributes: Record<string, AttributeValue>;
  scopeAttributes: Record<string, AttributeValue>;
  // Exception-specific fields
  exceptionStackTrace: string | ExceptionStackTraceNode[] | null;
  exceptionStackTraceRaw: string | null;
  exceptionMessage: string | null;
  exceptionType: string | null;
  title: string | null;
  eventName: ExceptionEventName | null;
  // Context
  screenName: string | null;
  interactionIds: string[];
  /** @deprecated Use interactionIds */
  interactions?: string[];
  userId: string | null;
  // Device/app info
  platform: PlatformType | null;
  osVersion: string | null;
  deviceModel: string | null;
  appVersion: string | null;
  appVersionCode: string | null;
  sdkVersion: string | null;
  bundleId: string | null;
  // Grouping
  groupId: string | null;
  signature: string | null;
  fingerprint: string | null;
}

