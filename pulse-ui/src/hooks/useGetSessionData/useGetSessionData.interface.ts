export interface SessionDataTimeRange {
  start: string;
  end: string;
}

export interface GetSessionDataParams {
  sessionId: string;
  timeRange: SessionDataTimeRange;
  enabled?: boolean;
}

export interface SessionTraceRow {
  traceId: string;
  spanId: string;
  parentSpanId: string;
  spanName: string;
  spanKind: string;
  serviceName: string;
  timestamp: string;
  duration: number;
  statusCode: string;
  spanType: string;
}

export interface SessionLogRow {
  traceId: string;
  spanId: string;
  timestamp: string;
  severityText: string;
  body: string;
  eventName: string;
}

export interface SessionExceptionRow {
  timestamp: string;
  eventName: string;
  title: string;
  exceptionMessage: string;
  exceptionType: string;
  screenName: string;
  traceId: string;
  spanId: string;
  groupId: string;
}

export interface RawDataResponse {
  fields: string[];
  rows: (string | number | null)[][];
}

export interface SessionDataResponse {
  traces: RawDataResponse | null;
  logs: RawDataResponse | null;
  exceptions: RawDataResponse | null;
}
