import { isEcommerceMockThemeEnabled } from "./mockEcommerceTheme";

/**
 * When ecommerce demo theme is on, re-label key funnel/journey rows so list + detail
 * match RCA “linked funnels” copy (IDs unchanged for mock API routing).
 */
export function applyEcommerceThemeToFunnelJourneyRow<
  T extends Record<string, unknown>,
>(row: T): T {
  if (!isEcommerceMockThemeEnabled()) return row;
  const id = row.id as string;
  if (id === "funnel-payment-001") {
    return {
      ...row,
      name: "Cart → Checkout → Payment",
      tags: ["checkout", "payment", "conversion", "pulse-mart"],
      description:
        "Pulse Mart funnel from cart through payment authorization—pair with Checkout and Payment heatmaps and session replays when conversion drops.",
    } as T;
  }
  if (id === "journey-onboarding-001") {
    return {
      ...row,
      name: "Browse → PDP → Add to cart → Order",
      tags: ["plp", "pdp", "browse-to-buy", "pulse-mart"],
      description:
        "Retail path from category listing through PDP and add-to-cart—use PLP/PDP heatmaps and session replays to find friction before checkout.",
    } as T;
  }
  return row;
}
