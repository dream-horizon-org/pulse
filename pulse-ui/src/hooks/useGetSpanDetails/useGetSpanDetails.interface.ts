export interface GetSpanDetailsParams {
  dataType: "TRACES" | "LOGS";
  traceId: string;
  spanId: string;
  timestamp: string;
  enabled?: boolean;
}

export interface SpanDetailsResponse {
  resourceAttributes: Record<string, string>;
  spanAttributes: Record<string, string>;
  events: Array<{
    timestamp: string;
    name: string;
    attributes: Record<string, string>;
  }>;
  links: Array<{
    traceId: string;
    spanId: string;
    attributes: Record<string, string>;
  }>;
}

export interface LogDetailsResponse {
  resourceAttributes: Record<string, string>;
  logAttributes: Record<string, string>;
  scopeAttributes: Record<string, string>;
  body: string;
  severityText: string;
  severityNumber: number;
}

