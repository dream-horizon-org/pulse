export interface InteractionDiscoverySuggestion {
  id: string;
  displayTitle: string;
  categoryLabel: string;
  startEvent: string;
  endEvent: string;
  description: string;
  insight: string;
  volumePerWeek: number;
  p50Ms: number;
  p95Ms: number;
  completionRatePercent: number;
  uniqueUsers: number;
  relevancePercent: number;
}

export interface GetInteractionDiscoveriesResponse {
  suggestions: InteractionDiscoverySuggestion[];
}
