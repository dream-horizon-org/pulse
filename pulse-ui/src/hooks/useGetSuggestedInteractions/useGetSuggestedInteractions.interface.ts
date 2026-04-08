export interface SuggestedInteractionEdge {
  from: string;
  to: string;
  meanGapS: number;
  medianGapS: number;
  cv: number;
  p5S: number;
  p95S: number;
}

export interface SuggestedInteraction {
  id: number;
  projectId: string;
  pattern: string[];
  totalOccurrences: number;
  uniqueSessions: number;
  sessionPct: number;
  meanSpanS: number;
  medianSpanS: number;
  p95SpanS: number;
  cv: number;
  edges: SuggestedInteractionEdge[];
  status: string;
  createdAt: string;
}

export interface GetSuggestedInteractionsResponse {
  suggestions: SuggestedInteraction[];
  totalSuggestions: number;
}
