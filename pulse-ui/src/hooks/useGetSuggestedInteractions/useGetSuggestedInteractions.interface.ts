export interface SuggestedInteractionEdge {
  from: string;
  to: string;
  meanGapS: number;
  medianGapS: number;
  cv: number;
  p5S: number;
  p95S: number;
}

export interface SuggestedEventProp {
  name: string;
  value: string;
  operator: string;
}

export interface SuggestedEvent {
  name: string;
  props: SuggestedEventProp[];
  isBlacklisted: boolean;
}

export interface SuggestedInteraction {
  id: number;
  projectId: string;
  events: SuggestedEvent[];
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
