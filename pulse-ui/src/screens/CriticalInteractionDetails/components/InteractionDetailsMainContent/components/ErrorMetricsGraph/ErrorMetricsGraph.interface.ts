import {
  InteractionDetailsGraphsData,
  InteractionDetailsMetricsData,
} from "../InteractionDetailsGraphs/interactionDetailsGraphs.interface";

export interface ErrorMetricsGraphProps {
  className?: string;
  graphData: InteractionDetailsGraphsData[];
  metrics: InteractionDetailsMetricsData;
}

export interface ErrorMetricsGraphData {
  timestamp: number;
  errorRate: number;
}

export interface ErrorMetrics {
  crashes: number;
  anr: number;
  networkErrors: number;
  errorRate: number;
}
