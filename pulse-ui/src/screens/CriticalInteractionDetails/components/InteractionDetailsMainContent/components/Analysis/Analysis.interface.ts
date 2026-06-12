import { CriticalInteractionDetailsFilterValues } from "../../../../CriticalInteractionDetails.interface";

export interface ComprehensiveAnalysis {
  totalInteractions: number;
  timeSeriesData: any[];
  breakdowns: any;
  metrics: any;
  anomalies: any[];
  sessions: any[];
}

export interface AnalysisProps {
  interactionName?: string;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
  startTime?: string;
  endTime?: string;
}

export const ANALYSIS_SECTIONS = {
  RELEASE: "release",
  REGIONAL: "regional",
  PLATFORM: "platform",
  NETWORK: "network",
  DEVICE: "device",
  OS: "os",
  LATENCY: "latency",
} as const;

export type AnalysisSection =
  (typeof ANALYSIS_SECTIONS)[keyof typeof ANALYSIS_SECTIONS];

export interface AnalysisSectionProps {
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
  startTimeMs: string;
  endTimeMs: string;
  shouldFetch: boolean;
  interactionName: string;
}
