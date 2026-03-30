import type { InteractionDiscoverySuggestion } from "../hooks/useGetInteractionDiscoveries/useGetInteractionDiscoveries.interface";

/**
 * Auto-discovered journeys for a D2C retail app ("Pulse Mart" demo narrative).
 * Pairs with {@link buildEcommerceInteractionJobs} so list + discovery tell one story.
 */
export const ECOMMERCE_INTERACTION_DISCOVERY_MOCK_SUGGESTIONS: InteractionDiscoverySuggestion[] =
  [
    {
      id: "eco-disc-1",
      displayTitle: "Product Page Open → Add to Cart Confirmed",
      categoryLabel: "Purchase intent",
      startEvent: "pdp_open",
      endEvent: "cart_line_item_confirmed",
      description:
        "Shoppers who open a PDP and successfully add a line item—core conversion signal before checkout.",
      insight:
        "Completion here is the strongest leading indicator of same-session checkout; watch P95 when inventory or variant APIs slow down.",
      volumePerWeek: 8420,
      p50Ms: 680,
      p95Ms: 2100,
      completionRatePercent: 54,
      uniqueUsers: 6120,
      relevancePercent: 100,
    },
    {
      id: "eco-disc-2",
      displayTitle: "Cart Screen Load → Checkout Step Visible",
      categoryLabel: "Checkout funnel",
      startEvent: "cart_fetch_request",
      endEvent: "shipping_step_visible",
      description:
        "Users who load the cart and reach the first checkout step in one flow.",
      insight:
        "Drop-off between these steps often reflects shipping estimator failures or stale cart totals—align with payment alerts.",
      volumePerWeek: 2910,
      p50Ms: 1200,
      p95Ms: 4800,
      completionRatePercent: 71,
      uniqueUsers: 2180,
      relevancePercent: 97,
    },
    {
      id: "eco-disc-3",
      displayTitle: "Payment Submit → Gateway Success",
      categoryLabel: "Revenue",
      startEvent: "payment_submit",
      endEvent: "payment_gateway_success",
      description: "End-to-end payment authorization for completed checkouts.",
      insight:
        "Highest business risk segment; small latency shifts correlate with abandoned carts in the last mile.",
      volumePerWeek: 1840,
      p50Ms: 2400,
      p95Ms: 6200,
      completionRatePercent: 88,
      uniqueUsers: 1620,
      relevancePercent: 100,
    },
    {
      id: "eco-disc-4",
      displayTitle: "Search Submitted → Results Rendered",
      categoryLabel: "Discovery",
      startEvent: "search_query_submitted",
      endEvent: "search_results_rendered",
      description:
        "Catalog search from query to first results paint—drives a large share of PDP entry.",
      insight:
        "Power users hit this path repeatedly; regressions often show up as higher zero-result rate before P95 moves.",
      volumePerWeek: 12400,
      p50Ms: 420,
      p95Ms: 1100,
      completionRatePercent: 93,
      uniqueUsers: 7800,
      relevancePercent: 95,
    },
    {
      id: "eco-disc-5",
      displayTitle: "Category PLP Request → Grid Ready",
      categoryLabel: "Browse",
      startEvent: "plp_request",
      endEvent: "plp_grid_ready",
      description:
        "Category listing fetch through sellable grid visible—entry to browse-to-buy.",
      insight:
        "Image CDN and pagination dominate; pair with home feed load when investigating browse churn.",
      volumePerWeek: 18600,
      p50Ms: 890,
      p95Ms: 2600,
      completionRatePercent: 91,
      uniqueUsers: 9400,
      relevancePercent: 92,
    },
    {
      id: "eco-disc-6",
      displayTitle: "Promo Apply Tap → Discount Applied",
      categoryLabel: "Promotions",
      startEvent: "promo_apply_tap",
      endEvent: "promo_discount_applied",
      description: "Promo code validation through successful cart repricing.",
      insight:
        "Lower completion often maps to eligibility rules or third-party coupon services—worth correlating with support tickets.",
      volumePerWeek: 3200,
      p50Ms: 1500,
      p95Ms: 5200,
      completionRatePercent: 62,
      uniqueUsers: 2800,
      relevancePercent: 78,
    },
  ];
