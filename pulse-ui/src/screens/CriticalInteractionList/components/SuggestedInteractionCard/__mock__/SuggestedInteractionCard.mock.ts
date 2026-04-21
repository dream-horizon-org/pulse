import { SuggestedInteraction } from "../../../../../hooks/useGetSuggestedInteractions/useGetSuggestedInteractions.interface";

export const mockSuggestion: SuggestedInteraction = {
  id: 1,
  projectId: "test-project",
  events: [
    { name: "Go shopping", props: [], isBlacklisted: false },
    { name: "Telescope selected", props: [], isBlacklisted: false },
  ],
  totalOccurrences: 8420,
  uniqueSessions: 6120,
  sessionPct: 72.5,
  meanSpanS: 0.72,
  medianSpanS: 0.68,
  p95SpanS: 2.1,
  cv: 0.12,
  edges: [
    {
      from: "Go shopping",
      to: "Telescope selected",
      meanGapS: 0.72,
      medianGapS: 0.68,
      cv: 0.12,
      p5S: 0.31,
      p95S: 2.1,
    },
  ],
  status: "PENDING",
  createdAt: "2024-01-01T00:00:00Z",
};

export const mockSuggestionLargeNumbers: SuggestedInteraction = {
  ...mockSuggestion,
  id: 2,
  totalOccurrences: 2_500_000,
};

export const mockSuggestionLongDurations: SuggestedInteraction = {
  ...mockSuggestion,
  id: 3,
  medianSpanS: 3.456,
  p95SpanS: 12.789,
};

export const mockSuggestionSmallNumbers: SuggestedInteraction = {
  ...mockSuggestion,
  id: 4,
  totalOccurrences: 500,
};

export const mockSuggestionSingleEvent: SuggestedInteraction = {
  ...mockSuggestion,
  id: 5,
  events: [{ name: "Checkout completed", props: [], isBlacklisted: false }],
  edges: [],
};

export const mockSuggestionThreeEvents: SuggestedInteraction = {
  ...mockSuggestion,
  id: 6,
  events: [
    { name: "Add to cart", props: [], isBlacklisted: false },
    { name: "Go shopping", props: [], isBlacklisted: false },
    { name: "Checkout completed", props: [], isBlacklisted: false },
  ],
};

export const mockSuggestionSpecialChars: SuggestedInteraction = {
  ...mockSuggestion,
  id: 7,
  events: [
    { name: "user-login_success", props: [], isBlacklisted: false },
    { name: "dashboard.view", props: [], isBlacklisted: false },
  ],
};
