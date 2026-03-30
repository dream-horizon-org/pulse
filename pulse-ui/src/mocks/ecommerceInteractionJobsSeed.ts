/**
 * Critical interactions for a cohesive D2C demo ("Pulse Mart"): browse → cart → checkout → pay.
 * Shape matches {@link MockDataStore.initializeJobs} default jobs for API compatibility.
 */
export function buildEcommerceInteractionJobs(
  now: number,
  oneDay: number,
): any[] {
  return [
    {
      id: 1,
      interactionName: "HomeFeedLoad",
      description:
        "Home merchandising rail and hero load: first byte through primary content ready. Sets the tone for session engagement.",
      status: "RUNNING",
      createdBy: "rahul.sharma@example.com",
      updatedBy: "rahul.sharma@example.com",
      createdAt: now - 5 * oneDay,
      updatedAt: now - 1 * oneDay,
      uptimeLowerLimit: 80,
      uptimeUpperLimit: 700,
      uptimeMidLimit: 320,
      interactionThreshold: 22000,
      eventSequence: [
        {
          eventName: "home_feed_request",
          props: [
            { propName: "locale", propValue: "string", operator: "EQUALS" },
            { propName: "segment", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "home_feed_ready",
          props: [
            { propName: "rail_count", propValue: "number", operator: "EQUALS" },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [],
    },
    {
      id: 2,
      interactionName: "CategoryListingLoad",
      description:
        "Category PLP: API and image pipeline until the product grid is interactive. High traffic path into PDP.",
      status: "RUNNING",
      createdBy: "priya.patel@example.com",
      updatedBy: "priya.patel@example.com",
      createdAt: now - 6 * oneDay,
      updatedAt: now - 2 * oneDay,
      uptimeLowerLimit: 90,
      uptimeUpperLimit: 900,
      uptimeMidLimit: 380,
      interactionThreshold: 28000,
      eventSequence: [
        {
          eventName: "plp_request",
          props: [
            {
              propName: "category_id",
              propValue: "string",
              operator: "EQUALS",
            },
            { propName: "sort", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "plp_grid_ready",
          props: [
            {
              propName: "sku_count",
              propValue: "number",
              operator: "EQUALS",
            },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [
        {
          eventName: "plp_empty_state",
          props: [
            {
              propName: "reason",
              propValue: "no_inventory",
              operator: "EQUALS",
            },
          ],
          isBlacklisted: true,
        },
      ],
    },
    {
      id: 3,
      interactionName: "ProductDetailLoad",
      description:
        "PDP open through sellable state: price, variants, and primary image resolved. Bridge from marketing into cart.",
      status: "RUNNING",
      createdBy: "rahul.sharma@example.com",
      updatedBy: "neha.singh@example.com",
      createdAt: now - 7 * oneDay,
      updatedAt: now - 1 * oneDay,
      uptimeLowerLimit: 70,
      uptimeUpperLimit: 650,
      uptimeMidLimit: 280,
      interactionThreshold: 20000,
      eventSequence: [
        {
          eventName: "pdp_open",
          props: [
            { propName: "sku_id", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "pdp_content_loaded",
          props: [
            {
              propName: "inventory_state",
              propValue: "string",
              operator: "EQUALS",
            },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [],
    },
    {
      id: 4,
      interactionName: "AddToCartLineItem",
      description:
        "Tap add-to-cart through server-acknowledged line item. Core micro-conversion before checkout.",
      status: "RUNNING",
      createdBy: "priya.patel@example.com",
      updatedBy: "priya.patel@example.com",
      createdAt: now - 8 * oneDay,
      updatedAt: now - 3 * oneDay,
      uptimeLowerLimit: 120,
      uptimeUpperLimit: 1100,
      uptimeMidLimit: 450,
      interactionThreshold: 35000,
      eventSequence: [
        {
          eventName: "add_to_cart_tap",
          props: [
            { propName: "sku_id", propValue: "string", operator: "EQUALS" },
            { propName: "qty", propValue: "number", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "cart_line_item_confirmed",
          props: [
            { propName: "cart_id", propValue: "string", operator: "EQUALS" },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [
        {
          eventName: "add_to_cart_rejected",
          props: [
            {
              propName: "reason",
              propValue: "insufficient_stock",
              operator: "EQUALS",
            },
          ],
          isBlacklisted: true,
        },
      ],
    },
    {
      id: 5,
      interactionName: "CartScreenLoad",
      description:
        "Cart fetch through screen ready with totals and line items. Where users reconcile tax, promos, and shipping hints.",
      status: "RUNNING",
      createdBy: "amit.kumar@example.com",
      updatedBy: "amit.kumar@example.com",
      createdAt: now - 9 * oneDay,
      updatedAt: now - 2 * oneDay,
      uptimeLowerLimit: 100,
      uptimeUpperLimit: 800,
      uptimeMidLimit: 360,
      interactionThreshold: 25000,
      eventSequence: [
        {
          eventName: "cart_fetch_request",
          props: [
            { propName: "cart_id", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "cart_screen_ready",
          props: [
            {
              propName: "line_item_count",
              propValue: "number",
              operator: "EQUALS",
            },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [],
    },
    {
      id: 6,
      interactionName: "ApplyPromoCode",
      description:
        "Promo apply through repriced cart. Sensitive to coupon provider latency and rule-engine errors.",
      status: "RUNNING",
      createdBy: "neha.singh@example.com",
      updatedBy: "priya.patel@example.com",
      createdAt: now - 10 * oneDay,
      updatedAt: now - 4 * oneDay,
      uptimeLowerLimit: 200,
      uptimeUpperLimit: 2200,
      uptimeMidLimit: 900,
      interactionThreshold: 45000,
      eventSequence: [
        {
          eventName: "promo_apply_tap",
          props: [
            { propName: "promo_code", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "promo_discount_applied",
          props: [
            {
              propName: "discount_cents",
              propValue: "number",
              operator: "EQUALS",
            },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [
        {
          eventName: "promo_validation_failed",
          props: [
            { propName: "error_code", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: true,
        },
      ],
    },
    {
      id: 7,
      interactionName: "CheckoutToShippingStep",
      description:
        "Checkout CTA through shipping step visible. Hand-off from cart math to fulfillment capture.",
      status: "RUNNING",
      createdBy: "rahul.sharma@example.com",
      updatedBy: "rahul.sharma@example.com",
      createdAt: now - 11 * oneDay,
      updatedAt: now - 2 * oneDay,
      uptimeLowerLimit: 150,
      uptimeUpperLimit: 1400,
      uptimeMidLimit: 550,
      interactionThreshold: 40000,
      eventSequence: [
        {
          eventName: "checkout_cta_tap",
          props: [
            { propName: "cart_id", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "shipping_step_visible",
          props: [
            {
              propName: "checkout_session_id",
              propValue: "string",
              operator: "EQUALS",
            },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [],
    },
    {
      id: 8,
      interactionName: "SaveShippingAddress",
      description:
        "Address save through validation OK. Blocks payment when carriers or tax engines reject input.",
      status: "RUNNING",
      createdBy: "priya.patel@example.com",
      updatedBy: "neha.singh@example.com",
      createdAt: now - 12 * oneDay,
      updatedAt: now - 5 * oneDay,
      uptimeLowerLimit: 180,
      uptimeUpperLimit: 1600,
      uptimeMidLimit: 700,
      interactionThreshold: 42000,
      eventSequence: [
        {
          eventName: "address_save_tap",
          props: [
            {
              propName: "country_code",
              propValue: "string",
              operator: "EQUALS",
            },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "address_validated",
          props: [
            {
              propName: "normalized",
              propValue: "true",
              operator: "EQUALS",
            },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [
        {
          eventName: "address_validation_failed",
          props: [
            { propName: "field", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: true,
        },
      ],
    },
    {
      id: 9,
      interactionName: "PaymentAuthorize",
      description:
        "Payment submit through gateway success. Directly tied to revenue recognition and chargebacks.",
      status: "RUNNING",
      createdBy: "priya.patel@example.com",
      updatedBy: "priya.patel@example.com",
      createdAt: now - 13 * oneDay,
      updatedAt: now - 1 * oneDay,
      uptimeLowerLimit: 220,
      uptimeUpperLimit: 2400,
      uptimeMidLimit: 1100,
      interactionThreshold: 65000,
      eventSequence: [
        {
          eventName: "payment_submit",
          props: [
            {
              propName: "amount_cents",
              propValue: "number",
              operator: "EQUALS",
            },
            {
              propName: "payment_method",
              propValue: "string",
              operator: "EQUALS",
            },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "payment_gateway_success",
          props: [
            {
              propName: "transaction_id",
              propValue: "string",
              operator: "EQUALS",
            },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [
        {
          eventName: "payment_gateway_declined",
          props: [
            {
              propName: "decline_code",
              propValue: "string",
              operator: "EQUALS",
            },
          ],
          isBlacklisted: true,
        },
      ],
    },
    {
      id: 10,
      interactionName: "OrderConfirmationDisplay",
      description:
        "Order placement through confirmation UI. Validates downstream OMS handoff and email receipt triggers.",
      status: "RUNNING",
      createdBy: "amit.kumar@example.com",
      updatedBy: "amit.kumar@example.com",
      createdAt: now - 14 * oneDay,
      updatedAt: now - 3 * oneDay,
      uptimeLowerLimit: 100,
      uptimeUpperLimit: 900,
      uptimeMidLimit: 400,
      interactionThreshold: 30000,
      eventSequence: [
        {
          eventName: "order_place_request",
          props: [
            {
              propName: "checkout_session_id",
              propValue: "string",
              operator: "EQUALS",
            },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "order_confirmation_visible",
          props: [
            { propName: "order_id", propValue: "string", operator: "EQUALS" },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [],
    },
    {
      id: 11,
      interactionName: "SearchToResultsLoad",
      description:
        "Search submit through results rendered. Drives long-tail revenue; latency shows up as query reformulation.",
      status: "RUNNING",
      createdBy: "neha.singh@example.com",
      updatedBy: "neha.singh@example.com",
      createdAt: now - 4 * oneDay,
      updatedAt: now - 1 * oneDay,
      uptimeLowerLimit: 50,
      uptimeUpperLimit: 500,
      uptimeMidLimit: 200,
      interactionThreshold: 15000,
      eventSequence: [
        {
          eventName: "search_query_submitted",
          props: [
            { propName: "query", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "search_results_rendered",
          props: [
            {
              propName: "hit_count",
              propValue: "number",
              operator: "EQUALS",
            },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [],
    },
    {
      id: 12,
      interactionName: "WishlistAddItem",
      description:
        "Wishlist heart tap through sync complete. Captures intent for remarketing when stock returns.",
      status: "RUNNING",
      createdBy: "rahul.sharma@example.com",
      updatedBy: "priya.patel@example.com",
      createdAt: now - 15 * oneDay,
      updatedAt: now - 6 * oneDay,
      uptimeLowerLimit: 60,
      uptimeUpperLimit: 550,
      uptimeMidLimit: 240,
      interactionThreshold: 18000,
      eventSequence: [
        {
          eventName: "wishlist_heart_tap",
          props: [
            { propName: "sku_id", propValue: "string", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
        {
          eventName: "wishlist_synced",
          props: [
            { propName: "saved", propValue: "true", operator: "EQUALS" },
            { propName: "status", propValue: "success", operator: "EQUALS" },
          ],
          isBlacklisted: false,
        },
      ],
      globalBlacklistedEvents: [],
    },
  ];
}
