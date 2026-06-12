import { AiInsights } from "../../../../hooks/useAiQuery";

/**
 * Props for the AiInsights component
 * @deprecated Use ResponseDetail in AiChat instead
 */
export interface AiInsightsProps {
  /** AI-generated insights */
  insights: AiInsights;
  /** Tables/databases analyzed */
  sourcesAnalyzed?: string[];
  /** Time range analyzed */
  timeRange?: { start: string; end: string };
}
