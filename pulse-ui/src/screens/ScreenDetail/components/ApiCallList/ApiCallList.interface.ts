export interface ApiCall {
  id: string;
  endpoint: string;
  method: string;
  avgResponseTime: number;
  requestCount: number;
  successRate: number;
  errorRate: number;
  trend: number[]; // Last 7 days trend
}

export interface ApiCallListProps {
  screenName?: string;
}
