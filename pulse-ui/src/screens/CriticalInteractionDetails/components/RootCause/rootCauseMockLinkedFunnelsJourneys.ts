import { isEcommerceMockThemeEnabled } from "../../../../mocks/mockEcommerceTheme";

export type RootCauseMockFunnelOrJourney = {
  id: string;
  name: string;
  type: "FUNNEL" | "JOURNEY";
  status: "ACTIVE";
  createdBy: string;
  createdAt: string;
  tags: string[];
  description: string;
};

/** Linked funnel/journey cards on Interaction → Root Cause (mock). */
export function getRootCauseMockLinkedFunnelsJourneys(): RootCauseMockFunnelOrJourney[] {
  if (isEcommerceMockThemeEnabled()) {
    // Same IDs as `funnelResponses` mocks so “View details” resolves in mock mode.
    return [
      {
        id: "funnel-payment-001",
        name: "Cart → Checkout → Payment",
        type: "FUNNEL",
        status: "ACTIVE",
        createdBy: "commerce-ops@example.com",
        createdAt: "2 days ago",
        tags: ["checkout", "payment", "conversion", "pulse-mart"],
        description:
          "Pulse Mart funnel from cart through payment—pair with Checkout and Payment screen heatmaps and session replays when this interaction regresses.",
      },
      {
        id: "journey-onboarding-001",
        name: "Browse → PDP → Add to cart → Order",
        type: "JOURNEY",
        status: "ACTIVE",
        createdBy: "growth@example.com",
        createdAt: "5 days ago",
        tags: ["plp", "pdp", "browse-to-buy", "pulse-mart"],
        description:
          "Category listing through product detail and add-to-cart—cross-check PLP/PDP heatmaps and linked session replays for stall points before checkout.",
      },
    ];
  }
  return [
    {
      id: "funnel-payment-001",
      name: "Payment Flow Conversion",
      type: "FUNNEL",
      status: "ACTIVE",
      createdBy: "sarah@example.com",
      createdAt: "3 days ago",
      tags: ["payment", "conversion", "critical"],
      description:
        "Tracks user conversion through the payment process including checkout and order completion.",
    },
    {
      id: "journey-onboarding-001",
      name: "User Onboarding Journey",
      type: "JOURNEY",
      status: "ACTIVE",
      createdBy: "alex@example.com",
      createdAt: "1 week ago",
      tags: ["onboarding", "ux", "retention"],
      description:
        "Maps the complete user journey from app launch to account creation and first purchase.",
    },
  ];
}
