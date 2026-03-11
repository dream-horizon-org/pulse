/**
 * Mock Responses for Breadcrumbs API
 *
 * Generates realistic breadcrumb events (business events from S3)
 * for the App Vitals occurrence detail view.
 */

import { createQueryJob } from "./realtimeQueryResponses";

const breadcrumbEventNames = [
  "app_open",
  "screen_view",
  "button_click",
  "add_to_cart",
  "purchase_initiated",
  "search_performed",
  "filter_applied",
  "item_favorited",
  "share_content",
  "notification_tapped",
  "pull_to_refresh",
  "tab_switched",
  "form_field_focused",
  "form_submitted",
  "video_played",
  "deeplink_opened",
  "permission_granted",
  "location_updated",
  "payment_method_selected",
  "checkout_step_completed",
];

const breadcrumbScreenNames = [
  "HomeScreen",
  "ProductListScreen",
  "ProductDetailScreen",
  "CartScreen",
  "CheckoutScreen",
  "PaymentScreen",
  "OrderConfirmationScreen",
  "ProfileScreen",
  "SettingsScreen",
  "SearchScreen",
  "NotificationsScreen",
  "WishlistScreen",
  "LoginScreen",
  "OnboardingScreen",
];

function randomChoice<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

function generateBreadcrumbProps(eventName: string): Record<string, unknown> {
  switch (eventName) {
    case "screen_view":
      return { previous_screen: randomChoice(breadcrumbScreenNames) };
    case "button_click":
      return {
        button_id: randomChoice(["btn_buy", "btn_add", "btn_cancel", "btn_back", "btn_submit", "btn_share"]),
        button_text: randomChoice(["Buy Now", "Add to Cart", "Cancel", "Go Back", "Submit", "Share"]),
      };
    case "add_to_cart":
      return {
        product_id: `SKU-${Math.floor(Math.random() * 9999)}`,
        product_name: randomChoice(["Running Shoes", "Wireless Earbuds", "Phone Case", "Laptop Stand"]),
        price: (Math.random() * 200 + 10).toFixed(2),
        quantity: Math.ceil(Math.random() * 3),
      };
    case "search_performed":
      return {
        query: randomChoice(["shoes", "headphones", "sale", "new arrivals", "gifts"]),
        results_count: Math.floor(Math.random() * 50),
      };
    case "purchase_initiated":
      return {
        cart_value: (Math.random() * 500 + 20).toFixed(2),
        item_count: Math.ceil(Math.random() * 5),
        currency: "INR",
      };
    case "filter_applied":
      return {
        filter_type: randomChoice(["price", "brand", "rating", "category"]),
        filter_value: randomChoice(["under_500", "Nike", "4_stars", "electronics"]),
      };
    case "notification_tapped":
      return {
        notification_type: randomChoice(["promo", "order_update", "reminder", "social"]),
        notification_id: `notif_${Math.floor(Math.random() * 9999)}`,
      };
    case "checkout_step_completed":
      return {
        step: randomChoice(["address", "payment", "review", "confirmation"]),
        step_number: Math.ceil(Math.random() * 4),
      };
    case "payment_method_selected":
      return {
        method: randomChoice(["UPI", "credit_card", "debit_card", "net_banking", "wallet"]),
      };
    default:
      return {};
  }
}

/**
 * Generate mock breadcrumb result rows matching the S3/Athena schema.
 * Each row is: { event_name, timestamp, screen_name, props }
 */
function generateBreadcrumbRows(
  errorTimestamp: string,
  count: number = 15,
): Record<string, unknown>[] {
  const errorMs = new Date(errorTimestamp).getTime();
  const windowStart = errorMs - 10 * 60 * 1000;
  const rows: Record<string, unknown>[] = [];

  for (let i = 0; i < count; i++) {
    const fraction = i / (count - 1 || 1);
    const ts = new Date(windowStart + fraction * (10 * 60 * 1000 + 30 * 1000));
    const eventName = randomChoice(breadcrumbEventNames);
    const screenName = randomChoice(breadcrumbScreenNames);
    const props = generateBreadcrumbProps(eventName);

    rows.push({
      event_name: eventName,
      timestamp: ts.toISOString().replace("T", " ").replace("Z", ""),
      screen_name: screenName,
      props: JSON.stringify(props),
    });
  }

  return rows;
}

/**
 * Handle POST /v1/breadcrumbs — returns either immediate results or a job for polling.
 * 50% chance of immediate, 50% chance of async (to test both paths).
 */
export function handleBreadcrumbsRequest(body: {
  sessionId?: string;
  errorTimestamp?: string;
}): {
  data: Record<string, unknown>;
  status: number;
  error?: { code: string; message: string; cause: string };
} {
  const { sessionId, errorTimestamp } = body;

  if (!sessionId || !sessionId.trim()) {
    return {
      data: {},
      status: 400,
      error: {
        code: "INVALID_REQUEST",
        message: "Session ID is required",
        cause: "Missing sessionId",
      },
    };
  }

  if (!errorTimestamp || !errorTimestamp.trim()) {
    return {
      data: {},
      status: 400,
      error: {
        code: "INVALID_REQUEST",
        message: "Error timestamp is required",
        cause: "Missing errorTimestamp",
      },
    };
  }

  const returnImmediate = Math.random() > 0.5;

  if (returnImmediate) {
    const rows = generateBreadcrumbRows(errorTimestamp, 12 + Math.floor(Math.random() * 10));
    return {
      data: {
        jobId: `bc_${Date.now()}`,
        status: "COMPLETED",
        message: "Breadcrumbs fetched successfully",
        resultData: rows,
        dataScannedInBytes: Math.floor(Math.random() * 50000) + 5000,
        createdAt: new Date().toISOString(),
        completedAt: new Date().toISOString(),
      },
      status: 200,
    };
  }

  const job = createQueryJob(
    `SELECT event_name, "timestamp", screen_name, props FROM pulse_athena_db.otel_data_1 WHERE session_id = '${sessionId}' LIMIT 100`,
  );

  return {
    data: {
      jobId: job.jobId,
      status: job.status,
      message:
        "Breadcrumb query submitted. Use GET /query/job/{jobId} to check status and get results.",
      resultData: null,
      createdAt: new Date().toISOString(),
    },
    status: 200,
  };
}
