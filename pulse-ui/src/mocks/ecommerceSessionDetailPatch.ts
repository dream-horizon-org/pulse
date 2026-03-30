import type { SessionDetailData } from "../services/sessionReplay/mockSessionDetail";
import { isEcommerceMockThemeEnabled } from "./mockEcommerceTheme";
import { getActiveSessionReplayDetailInteractionOrder } from "./mockPulseProjectRegistry";

/** Legacy session-replay player mock: align critical interactions + copy with ecommerce theme. */
export function patchSessionDetailDataForEcommerceTheme(
  data: SessionDetailData,
): SessionDetailData {
  if (!isEcommerceMockThemeEnabled()) return data;
  const order = getActiveSessionReplayDetailInteractionOrder();
  const a = order[0] ?? "CategoryListingLoad";
  const b = order[1] ?? "AddToCartLineItem";
  const c = order[2] ?? "PaymentAuthorize";

  return {
    ...data,
    detectedIssues: data.detectedIssues.map((issue) => {
      if (issue.id !== "issue_timeout_1") return issue;
      return {
        ...issue,
        affectedFeature: "Checkout payment",
        description: "Payment API failed to respond within timeout threshold",
        userFacingImpact:
          "User could not complete $99.99 order payment. Transaction was not processed.",
        technicalCause:
          "POST /api/checkout/payment returned 504 Gateway Timeout after 30s",
      };
    }),
    sessionIntent: data.sessionIntent
      ? {
          ...data.sessionIntent,
          primary: "Complete purchase",
          abandonedAt: "Payment",
        }
      : data.sessionIntent,
    businessContext: data.businessContext
      ? {
          ...data.businessContext,
          conversionGoal: "Order paid",
          funnelStep: "Step 4 of 5: Pay with card",
          featuresUsed: [
            "Search",
            "Product detail",
            "Cart",
            "Checkout",
            "Payment",
            "Wallet",
          ],
          featureEngagement: {
            Search: 20000,
            "Product detail": 45000,
            Cart: 35000,
            Checkout: 42000,
            Payment: 30000,
          },
          experiments: [
            {
              id: "exp_one_click_checkout",
              name: "One-tap checkout",
              variant: "Variant B",
            },
          ],
        }
      : data.businessContext,
    technicalContext: data.technicalContext
      ? {
          ...data.technicalContext,
          reproductionSteps: [
            "Open Pulse Mart → product detail on iOS",
            "Add to cart and open checkout",
            'Tap "Place order" / pay',
            "Payment API times out after 30s",
            "Error banner appears",
          ],
        }
      : data.technicalContext,
    uxMetrics: data.uxMetrics
      ? {
          ...data.uxMetrics,
          viewportTime: {
            HomeScreen: 20000,
            ProductListScreen: 25000,
            ProductDetailScreen: 45000,
            CheckoutFormScreen: 42000,
            PaymentScreen: 30000,
          },
        }
      : data.uxMetrics,
    criticalInteractions: [
      {
        interactionId: 1,
        interactionName: a,
        displayName: "Category listing",
        status: "success",
        timestamp: 15000,
        latency: 420,
        apdexScore: 0.85,
        businessValue: "Discovery",
        revenueImpact: 49,
      },
      {
        interactionId: 2,
        interactionName: b,
        displayName: "Add to cart",
        status: "success",
        timestamp: 45000,
        latency: 890,
        apdexScore: 0.82,
        businessValue: "Cart add",
      },
      {
        interactionId: 3,
        interactionName: c,
        displayName: "Payment authorize",
        status: "failed",
        timestamp: 77000,
        latency: 30100,
        apdexScore: 0,
        businessValue: "Order revenue",
        revenueImpact: 99.99,
      },
    ],
  };
}
