export interface NetworkDetailProps {
  // Props can be extended as needed
}

export interface ApiCallMetrics {
  avgRequestTime: number;
  totalRequests: number;
  successRate: number;
  failureRate: number;
  p50: number;
  p95: number;
  p99: number;
}
