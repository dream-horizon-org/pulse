export interface NetworkListProps {
  screenName?: string;
  showHeader?: boolean;
  showFilters?: boolean;
  externalStartTime?: string;
  externalEndTime?: string;
  externalFilters?: Array<{
    field: string;
    operator: "LIKE" | "EQ";
    value: string[];
  }>;
}

export interface NetworkApi {
  id: string;
  endpoint: string;
  operationName?: string;
  operationType?: string;
  method?: string;
  avgResponseTime: number;
  requestCount: number;
  successRate: number;
  errorRate: number;
  p50: number;
  p95: number;
  p99: number;
  lastCalled: string;
  screenName?: string;
  allSessions?: number;
}
