/**
 * Funnel Analysis & Journey Explorer Mock Responses
 */

import { MockResponse, MockRequest } from "../types";

const MOCK_FUNNEL_ANALYZE_RESPONSE = {
  steps: [
    { stepName: "Screen_View: Home", count: 14200, conversionRate: 100, dropoffRate: 0 },
    { stepName: "Screen_View: Product Detail", count: 9680, conversionRate: 68.2, dropoffRate: 31.8 },
    { stepName: "Tap: Add to Cart", count: 6840, conversionRate: 48.2, dropoffRate: 29.3 },
    { stepName: "Tap: Checkout", count: 5100, conversionRate: 35.9, dropoffRate: 25.4 },
    { stepName: "Tap: Place Order", count: 4600, conversionRate: 32.4, dropoffRate: 9.8 },
  ],
  totalEnteredUsers: 14200,
  overallConversionRate: 32.4,
};

const MOCK_FUNNEL_HEALTH_RESPONSE = {
  steps: [
    { stepLevel: 1, stepName: "Screen_View: Home", totalUsers: 14200, crashUsers: 42, anrUsers: 18, nonFatalUsers: 120, crashRate: 0.3, anrRate: 0.13, nonFatalRate: 0.85 },
    { stepLevel: 2, stepName: "Screen_View: Product Detail", totalUsers: 9680, crashUsers: 38, anrUsers: 22, nonFatalUsers: 95, crashRate: 0.39, anrRate: 0.23, nonFatalRate: 0.98 },
    { stepLevel: 3, stepName: "Tap: Add to Cart", totalUsers: 6840, crashUsers: 15, anrUsers: 8, nonFatalUsers: 52, crashRate: 0.22, anrRate: 0.12, nonFatalRate: 0.76 },
    { stepLevel: 4, stepName: "Tap: Checkout", totalUsers: 5100, crashUsers: 28, anrUsers: 14, nonFatalUsers: 41, crashRate: 0.55, anrRate: 0.27, nonFatalRate: 0.8 },
    { stepLevel: 5, stepName: "Tap: Place Order", totalUsers: 4600, crashUsers: 52, anrUsers: 31, nonFatalUsers: 22, crashRate: 1.13, anrRate: 0.67, nonFatalRate: 0.48 },
  ],
  totalCrashUsers: 175,
  totalAnrUsers: 93,
  totalNonFatalUsers: 330,
};

const MOCK_FUNNEL_SESSIONS_RESPONSE = {
  stepLevel: 1,
  stepName: "Screen_View: Home",
  totalAffectedSessions: 3,
  sessions: [
    { sessionId: "sess-001", userId: "user-42", eventName: "device.crash", exceptionType: "NullPointerException", exceptionMessage: "Attempt to invoke virtual method on a null object", title: "NullPointerException in HomeFragment", screenName: "HomeScreen", timestamp: "2026-03-01T10:23:45Z", groupId: "grp-101", platform: "Android", appVersion: "4.2.1", deviceModel: "Samsung Galaxy S24" },
    { sessionId: "sess-002", userId: "user-88", eventName: "device.anr", exceptionType: "ANR", exceptionMessage: "Input dispatching timed out", title: "ANR in HomeActivity", screenName: "HomeScreen", timestamp: "2026-03-01T11:15:22Z", groupId: "grp-102", platform: "Android", appVersion: "4.2.0", deviceModel: "Pixel 8" },
    { sessionId: "sess-003", userId: "user-156", eventName: "device.crash", exceptionType: "ArrayIndexOutOfBoundsException", exceptionMessage: "Index 5 out of bounds for length 3", title: "AIOOBE in ProductAdapter", screenName: "HomeScreen", timestamp: "2026-03-01T14:42:11Z", groupId: "grp-103", platform: "Android", appVersion: "4.2.1", deviceModel: "OnePlus 12" },
  ],
};

const MOCK_FUNNEL_CONVERSION_TREND = {
  totalConversionRate: 32.4,
  conversionTrend: 2.1,
  medianTimes: [null, 4.2, 12.8, 8.6, 45.3],
};

const MOCK_JOURNEY_FORWARD = {
  nodes: [
    { name: "App_Launch" },
    { name: "Screen_View: Home" },
    { name: "Screen_View: Search" },
    { name: "Screen_View: Product Detail" },
    { name: "Screen_View: Cart" },
    { name: "Screen_View: Profile" },
    { name: "Tap: Add to Cart" },
    { name: "Tap: Checkout" },
    { name: "Tap: Place Order" },
    { name: "Screen_View: Order Confirmation" },
    { name: "App_Background" },
    { name: "Exit" },
  ],
  links: [
    { source: "App_Launch", target: "Screen_View: Home", value: 14200 },
    { source: "Screen_View: Home", target: "Screen_View: Search", value: 4800 },
    { source: "Screen_View: Home", target: "Screen_View: Product Detail", value: 5200 },
    { source: "Screen_View: Home", target: "Screen_View: Profile", value: 1800 },
    { source: "Screen_View: Home", target: "Exit", value: 2400 },
    { source: "Screen_View: Search", target: "Screen_View: Product Detail", value: 3600 },
    { source: "Screen_View: Search", target: "Exit", value: 1200 },
    { source: "Screen_View: Product Detail", target: "Tap: Add to Cart", value: 5400 },
    { source: "Screen_View: Product Detail", target: "Exit", value: 3400 },
    { source: "Screen_View: Profile", target: "Exit", value: 1800 },
    { source: "Tap: Add to Cart", target: "Screen_View: Cart", value: 4800 },
    { source: "Tap: Add to Cart", target: "Exit", value: 600 },
    { source: "Screen_View: Cart", target: "Tap: Checkout", value: 3900 },
    { source: "Screen_View: Cart", target: "Exit", value: 900 },
    { source: "Tap: Checkout", target: "Tap: Place Order", value: 3200 },
    { source: "Tap: Checkout", target: "Exit", value: 700 },
    { source: "Tap: Place Order", target: "Screen_View: Order Confirmation", value: 3000 },
    { source: "Tap: Place Order", target: "Exit", value: 200 },
    { source: "Screen_View: Order Confirmation", target: "App_Background", value: 2200 },
    { source: "Screen_View: Order Confirmation", target: "Exit", value: 800 },
  ],
};

const MOCK_JOURNEY_REVERSE = {
  nodes: [
    { name: "App_Crash" },
    { name: "Tap: Place Order" },
    { name: "Tap: Checkout" },
    { name: "Tap: Apply Coupon" },
    { name: "Screen_View: Cart" },
    { name: "Screen_View: Product Detail" },
    { name: "Tap: Add to Cart" },
    { name: "Screen_View: Search" },
    { name: "Screen_View: Home" },
    { name: "Deep_Link_Opened" },
  ],
  links: [
    { source: "Tap: Place Order", target: "App_Crash", value: 320 },
    { source: "Tap: Checkout", target: "App_Crash", value: 180 },
    { source: "Tap: Apply Coupon", target: "App_Crash", value: 95 },
    { source: "Screen_View: Cart", target: "Tap: Place Order", value: 280 },
    { source: "Screen_View: Cart", target: "Tap: Checkout", value: 160 },
    { source: "Screen_View: Cart", target: "Tap: Apply Coupon", value: 85 },
    { source: "Screen_View: Product Detail", target: "Screen_View: Cart", value: 340 },
    { source: "Screen_View: Product Detail", target: "App_Crash", value: 45 },
    { source: "Tap: Add to Cart", target: "Screen_View: Cart", value: 180 },
    { source: "Screen_View: Search", target: "Screen_View: Product Detail", value: 200 },
    { source: "Screen_View: Home", target: "Screen_View: Product Detail", value: 140 },
    { source: "Screen_View: Home", target: "Screen_View: Search", value: 160 },
    { source: "Deep_Link_Opened", target: "Screen_View: Product Detail", value: 45 },
  ],
};

const MOCK_GROUPED_DATA: Record<string, any> = {
  OS: [
    {
      groupValue: "iOS",
      steps: [
        { stepName: "Screen_View: Home", count: 8200, conversionRate: 100, dropoffRate: 0, medianTimeToStep: null },
        { stepName: "Screen_View: Product Detail", count: 5900, conversionRate: 72.0, dropoffRate: 28.0, medianTimeToStep: 3.8 },
        { stepName: "Tap: Add to Cart", count: 4300, conversionRate: 52.4, dropoffRate: 27.1, medianTimeToStep: 11.2 },
        { stepName: "Tap: Checkout", count: 3300, conversionRate: 40.2, dropoffRate: 23.3, medianTimeToStep: 7.9 },
        { stepName: "Tap: Place Order", count: 3000, conversionRate: 36.6, dropoffRate: 9.1, medianTimeToStep: 42.1 },
      ],
    },
    {
      groupValue: "Android",
      steps: [
        { stepName: "Screen_View: Home", count: 6000, conversionRate: 100, dropoffRate: 0, medianTimeToStep: null },
        { stepName: "Screen_View: Product Detail", count: 3780, conversionRate: 63.0, dropoffRate: 37.0, medianTimeToStep: 4.8 },
        { stepName: "Tap: Add to Cart", count: 2540, conversionRate: 42.3, dropoffRate: 32.8, medianTimeToStep: 14.9 },
        { stepName: "Tap: Checkout", count: 1800, conversionRate: 30.0, dropoffRate: 29.1, medianTimeToStep: 9.6 },
        { stepName: "Tap: Place Order", count: 1600, conversionRate: 26.7, dropoffRate: 11.1, medianTimeToStep: 50.8 },
      ],
    },
  ],
};

const MOCK_FUNNEL_EVENTS = [
  "Screen_View: Home",
  "Screen_View: Cart",
  "Screen_View: Product Detail",
  "Screen_View: Profile",
  "Screen_View: Search",
  "Screen_View: Checkout",
  "Screen_View: Order Confirmation",
  "Screen_View: Settings",
  "Tap: Add to Cart",
  "Tap: Checkout",
  "Tap: Search",
  "Tap: Apply Coupon",
  "Tap: Place Order",
  "Tap: Sign In",
  "Tap: Share",
  "App_Launch",
  "App_Background",
  "App_Crash",
  "Push_Opened",
  "Deep_Link_Opened",
];

const MOCK_FUNNEL_FILTER_OPTIONS: Record<string, string[]> = {
  "App Version": ["4.2.1", "4.2.0", "4.1.9", "4.1.8", "4.1.7"],
  "Device Model": [
    "iPhone 15 Pro", "iPhone 14", "iPhone 13",
    "Samsung Galaxy S24", "Samsung Galaxy S23",
    "Pixel 8", "Pixel 7", "OnePlus 12",
  ],
  OS: ["iOS", "Android"],
  Country: ["United States", "India", "United Kingdom", "Germany", "Japan", "Brazil", "Canada", "Australia"],
  City: ["San Francisco", "New York", "London", "Mumbai", "Berlin", "Tokyo", "São Paulo", "Toronto", "Sydney"],
};

export function handleFunnelEndpoints(
  pathname: string,
  method: string,
  request: MockRequest,
): MockResponse {
  if (pathname.includes("/v1/funnel/analyze") && method === "POST") {
    return { data: MOCK_FUNNEL_ANALYZE_RESPONSE, status: 200 };
  }

  if (pathname.includes("/v1/funnel/health") && method === "POST") {
    return { data: MOCK_FUNNEL_HEALTH_RESPONSE, status: 200 };
  }

  if (pathname.includes("/v1/funnel/sessions") && method === "POST") {
    let body: any = {};
    try { body = JSON.parse(request.body || "{}"); } catch { /* ignore */ }
    const resp = { ...MOCK_FUNNEL_SESSIONS_RESPONSE };
    if (body.stepLevel) resp.stepLevel = body.stepLevel;
    return { data: resp, status: 200 };
  }

  if (pathname.includes("/v1/funnel/trend") && method === "POST") {
    return { data: MOCK_FUNNEL_CONVERSION_TREND, status: 200 };
  }

  if (pathname.includes("/v1/funnel/grouped") && method === "POST") {
    let body: any = {};
    try { body = JSON.parse(request.body || "{}"); } catch { /* ignore */ }
    const groupBy = body.groupBy || "OS";
    return { data: { groups: MOCK_GROUPED_DATA[groupBy] || [] }, status: 200 };
  }

  if (pathname.includes("/v1/funnel/events") && method === "GET") {
    return { data: { events: MOCK_FUNNEL_EVENTS }, status: 200 };
  }

  if (pathname.includes("/v1/funnel/filters") && method === "GET") {
    return { data: { filters: MOCK_FUNNEL_FILTER_OPTIONS }, status: 200 };
  }

  if (pathname.includes("/v1/journey/explore") && method === "POST") {
    let body: any = {};
    try { body = JSON.parse(request.body || "{}"); } catch { /* ignore */ }
    const direction = body.direction || "forward";
    const data = direction === "reverse" ? MOCK_JOURNEY_REVERSE : MOCK_JOURNEY_FORWARD;
    return { data, status: 200 };
  }

  return { data: { message: "Unknown funnel endpoint" }, status: 404 };
}
