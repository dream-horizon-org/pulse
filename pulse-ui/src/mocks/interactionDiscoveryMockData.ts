import type { InteractionDiscoverySuggestion } from "../hooks/useGetInteractionDiscoveries/useGetInteractionDiscoveries.interface";

/**
 * Static suggestions for GET /v1/interactions/discoveries (mock server only until backend exists).
 */
export const INTERACTION_DISCOVERY_MOCK_SUGGESTIONS: InteractionDiscoverySuggestion[] =
  [
    {
      id: "disc-1",
      displayTitle: "Payment Initiated → Payment Result",
      categoryLabel: "Purchase flow",
      startEvent: "payment_initiated",
      endEvent: "payment_result",
      description:
        "Tracks users from starting checkout through receiving a payment outcome.",
      insight:
        "This is the most critical step in the purchase flow; completion and latency directly affect revenue.",
      volumePerWeek: 563,
      p50Ms: 3700,
      p95Ms: 7600,
      completionRatePercent: 60,
      uniqueUsers: 405,
      relevancePercent: 100,
    },
    {
      id: "disc-2",
      displayTitle: "App Launch to Home Screen",
      categoryLabel: "App launch",
      startEvent: "app_launch",
      endEvent: "home_screen_visible",
      description: "Cold start through first meaningful home content.",
      insight:
        "High volume and strong correlation with day-one retention; prioritize sub‑3s P50 when possible.",
      volumePerWeek: 12400,
      p50Ms: 2100,
      p95Ms: 5200,
      completionRatePercent: 92,
      uniqueUsers: 8900,
      relevancePercent: 98,
    },
    {
      id: "disc-3",
      displayTitle: "Search Open → Search Results Loaded",
      categoryLabel: "Search",
      startEvent: "search_opened",
      endEvent: "search_results_loaded",
      description: "Time from opening search to results rendered.",
      insight:
        "Frequent path for power users; latency spikes here often map to backend or cache issues.",
      volumePerWeek: 3200,
      p50Ms: 890,
      p95Ms: 2400,
      completionRatePercent: 88,
      uniqueUsers: 2100,
      relevancePercent: 94,
    },
    {
      id: "disc-4",
      displayTitle: "Profile Tab → Profile Edited",
      categoryLabel: "Account",
      startEvent: "profile_tab_open",
      endEvent: "profile_save_success",
      description: "Users who open profile and complete an edit successfully.",
      insight:
        "Lower volume but high intent; failures often reflect form or API validation problems.",
      volumePerWeek: 412,
      p50Ms: 4500,
      p95Ms: 12000,
      completionRatePercent: 45,
      uniqueUsers: 380,
      relevancePercent: 72,
    },
    {
      id: "disc-5",
      displayTitle: "Add to Cart → Cart Viewed",
      categoryLabel: "Purchase flow",
      startEvent: "add_to_cart",
      endEvent: "cart_viewed",
      description: "Adds an item then opens the cart within the same session.",
      insight:
        "Strong funnel signal before checkout; use to tune merchandising and cart UX.",
      volumePerWeek: 2100,
      p50Ms: 1200,
      p95Ms: 3100,
      completionRatePercent: 78,
      uniqueUsers: 1650,
      relevancePercent: 91,
    },
  ];
