import {
  InteractionDetailsGraphsData,
  InteractionDetailsMetricsData,
} from "../InteractionDetailsGraphs/interactionDetailsGraphs.interface";

export interface ApdexWithLatencyGraphProps {
  useCaseName?: string;
  startTime?: string;
  endTime?: string;
  filters?: any;
  className?: string;
  graphData: InteractionDetailsGraphsData;
  metrics: InteractionDetailsMetricsData;
}

export interface ApdexWithLatencyGraphData {
  timestamp: number;
  apdex: number;
}

export interface LatencyPercentiles {
  p50: number;
  p90: number;
  p95: number;
}

export interface ApdexWithLatencyMetrics {
  apdexScore: number;
  latencyPercentiles: LatencyPercentiles;
  frozenFrames: number;
}
