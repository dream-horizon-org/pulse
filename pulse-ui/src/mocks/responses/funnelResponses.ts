/**
 * Funnel Analysis & Journey Explorer Mock Responses
 */

import { MockRequest, MockResponse } from "../types";

const MOCK_PAYMENT_FUNNEL_ANALYZE_RESPONSE = {
  steps: [
    {
      stepName: "Screen_View: Cart",
      count: 8750,
      conversionRate: 100,
      dropoffRate: 0,
    },
    {
      stepName: "Tap: Checkout",
      count: 6820,
      conversionRate: 77.9,
      dropoffRate: 22.1,
    },
    {
      stepName: "Screen_View: Payment",
      count: 5940,
      conversionRate: 67.9,
      dropoffRate: 12.9,
    },
    {
      stepName: "Tap: Enter Payment Details",
      count: 4980,
      conversionRate: 56.9,
      dropoffRate: 16.2,
    },
    {
      stepName: "Tap: Place Order",
      count: 4230,
      conversionRate: 48.3,
      dropoffRate: 15.1,
    },
    {
      stepName: "Screen_View: Order Confirmation",
      count: 4050,
      conversionRate: 46.3,
      dropoffRate: 4.3,
    },
  ],
  totalEnteredUsers: 8750,
  overallConversionRate: 46.3,
};

const MOCK_FUNNEL_HEALTH_RESPONSE = {
  steps: [
    {
      stepLevel: 1,
      stepName: "Screen_View: Home",
      totalUsers: 14200,
      crashUsers: 42,
      anrUsers: 18,
      nonFatalUsers: 120,
      crashRate: 0.3,
      anrRate: 0.13,
      nonFatalRate: 0.85,
    },
    {
      stepLevel: 2,
      stepName: "Screen_View: Product Detail",
      totalUsers: 9680,
      crashUsers: 38,
      anrUsers: 22,
      nonFatalUsers: 95,
      crashRate: 0.39,
      anrRate: 0.23,
      nonFatalRate: 0.98,
    },
    {
      stepLevel: 3,
      stepName: "Tap: Add to Cart",
      totalUsers: 6840,
      crashUsers: 15,
      anrUsers: 8,
      nonFatalUsers: 52,
      crashRate: 0.22,
      anrRate: 0.12,
      nonFatalRate: 0.76,
    },
    {
      stepLevel: 4,
      stepName: "Tap: Checkout",
      totalUsers: 5100,
      crashUsers: 28,
      anrUsers: 14,
      nonFatalUsers: 41,
      crashRate: 0.55,
      anrRate: 0.27,
      nonFatalRate: 0.8,
    },
    {
      stepLevel: 5,
      stepName: "Tap: Place Order",
      totalUsers: 4600,
      crashUsers: 52,
      anrUsers: 31,
      nonFatalUsers: 22,
      crashRate: 1.13,
      anrRate: 0.67,
      nonFatalRate: 0.48,
    },
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
    {
      sessionId: "sess-001",
      userId: "user-42",
      eventName: "device.crash",
      exceptionType: "NullPointerException",
      exceptionMessage: "Attempt to invoke virtual method on a null object",
      title: "NullPointerException in HomeFragment",
      screenName: "HomeScreen",
      timestamp: "2026-03-01T10:23:45Z",
      groupId: "grp-101",
      platform: "Android",
      appVersion: "4.2.1",
      deviceModel: "Samsung Galaxy S24",
    },
    {
      sessionId: "sess-002",
      userId: "user-88",
      eventName: "device.anr",
      exceptionType: "ANR",
      exceptionMessage: "Input dispatching timed out",
      title: "ANR in HomeActivity",
      screenName: "HomeScreen",
      timestamp: "2026-03-01T11:15:22Z",
      groupId: "grp-102",
      platform: "Android",
      appVersion: "4.2.0",
      deviceModel: "Pixel 8",
    },
    {
      sessionId: "sess-003",
      userId: "user-156",
      eventName: "device.crash",
      exceptionType: "ArrayIndexOutOfBoundsException",
      exceptionMessage: "Index 5 out of bounds for length 3",
      title: "AIOOBE in ProductAdapter",
      screenName: "HomeScreen",
      timestamp: "2026-03-01T14:42:11Z",
      groupId: "grp-103",
      platform: "Android",
      appVersion: "4.2.1",
      deviceModel: "OnePlus 12",
    },
  ],
};

const MOCK_PAYMENT_FUNNEL_CONVERSION_TREND = {
  totalConversionRate: 46.3,
  conversionTrend: -1.8,
  medianTimes: [null, 5.1, 18.4, 12.7, 8.9, 3.2],
};

/** Build analyze + trend payloads so step names match the request (list/detail pages stay aligned). */
function buildMockFunnelAnalyzeAndTrendFromSteps(body: {
  steps?: Array<{ eventName?: string }>;
}): {
  analyze: {
    steps: Array<{
      stepName: string;
      count: number;
      conversionRate: number;
      dropoffRate: number;
    }>;
    totalEnteredUsers: number;
    overallConversionRate: number;
  };
  trend: {
    totalConversionRate: number;
    conversionTrend: number;
    medianTimes: (number | null)[];
  };
} {
  const steps = (body.steps || []).filter((s) => (s?.eventName || "").trim());
  if (steps.length < 2) {
    return {
      analyze: {
        steps: [],
        totalEnteredUsers: 0,
        overallConversionRate: 0,
      },
      trend: {
        totalConversionRate: 0,
        conversionTrend: 0,
        medianTimes: [],
      },
    };
  }

  let hash = 0;
  for (const s of steps) {
    const name = s.eventName || "";
    for (let i = 0; i < name.length; i++) {
      hash = (hash * 31 + name.charCodeAt(i)) | 0;
    }
  }
  const base = 6000 + (Math.abs(hash) % 4000);
  const stepMultipliers = [1, 0.82, 0.71, 0.62, 0.54, 0.48, 0.43, 0.39, 0.36];

  const stepResults: Array<{
    stepName: string;
    count: number;
    conversionRate: number;
    dropoffRate: number;
  }> = [];

  for (let i = 0; i < steps.length; i++) {
    const eventName = steps[i].eventName!.trim();
    const count =
      i === 0
        ? base
        : Math.max(
            1,
            Math.round(
              stepResults[i - 1].count *
                (stepMultipliers[i] ?? 0.35 + (i % 3) * 0.02),
            ),
          );
    const conversionRate = (count / base) * 100;
    const dropoffRate =
      i === 0
        ? 0
        : (1 - count / stepResults[i - 1].count) * 100;
    stepResults.push({
      stepName: eventName,
      count,
      conversionRate: Math.round(conversionRate * 10) / 10,
      dropoffRate: Math.round(dropoffRate * 10) / 10,
    });
  }

  const last = stepResults[stepResults.length - 1];
  const overallConversionRate = last.conversionRate;

  const medianTimes: (number | null)[] = [null];
  let t = 2.5 + (Math.abs(hash) % 7);
  for (let i = 1; i < steps.length; i++) {
    t += 3 + (i % 4) * 2.4;
    medianTimes.push(Math.round(t * 10) / 10);
  }

  return {
    analyze: {
      steps: stepResults,
      totalEnteredUsers: base,
      overallConversionRate,
    },
    trend: {
      totalConversionRate: overallConversionRate,
      conversionTrend: Math.round(((Math.abs(hash) % 17) - 8) * 10) / 10,
      medianTimes,
    },
  };
}

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
    {
      source: "Screen_View: Home",
      target: "Screen_View: Product Detail",
      value: 5200,
    },
    {
      source: "Screen_View: Home",
      target: "Screen_View: Profile",
      value: 1800,
    },
    { source: "Screen_View: Home", target: "Exit", value: 2400 },
    {
      source: "Screen_View: Search",
      target: "Screen_View: Product Detail",
      value: 3600,
    },
    { source: "Screen_View: Search", target: "Exit", value: 1200 },
    {
      source: "Screen_View: Product Detail",
      target: "Tap: Add to Cart",
      value: 5400,
    },
    { source: "Screen_View: Product Detail", target: "Exit", value: 3400 },
    { source: "Screen_View: Profile", target: "Exit", value: 1800 },
    { source: "Tap: Add to Cart", target: "Screen_View: Cart", value: 4800 },
    { source: "Tap: Add to Cart", target: "Exit", value: 600 },
    { source: "Screen_View: Cart", target: "Tap: Checkout", value: 3900 },
    { source: "Screen_View: Cart", target: "Exit", value: 900 },
    { source: "Tap: Checkout", target: "Tap: Place Order", value: 3200 },
    { source: "Tap: Checkout", target: "Exit", value: 700 },
    {
      source: "Tap: Place Order",
      target: "Screen_View: Order Confirmation",
      value: 3000,
    },
    { source: "Tap: Place Order", target: "Exit", value: 200 },
    {
      source: "Screen_View: Order Confirmation",
      target: "App_Background",
      value: 2200,
    },
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
    {
      source: "Screen_View: Product Detail",
      target: "Screen_View: Cart",
      value: 340,
    },
    { source: "Screen_View: Product Detail", target: "App_Crash", value: 45 },
    { source: "Tap: Add to Cart", target: "Screen_View: Cart", value: 180 },
    {
      source: "Screen_View: Search",
      target: "Screen_View: Product Detail",
      value: 200,
    },
    {
      source: "Screen_View: Home",
      target: "Screen_View: Product Detail",
      value: 140,
    },
    { source: "Screen_View: Home", target: "Screen_View: Search", value: 160 },
    {
      source: "Deep_Link_Opened",
      target: "Screen_View: Product Detail",
      value: 45,
    },
  ],
};

const MOCK_ONBOARDING_JOURNEY_RESPONSE = {
  nodes: [
    { name: "App_Launch" },
    { name: "Screen_View: Welcome" },
    { name: "Tap: Get Started" },
    { name: "Screen_View: Sign Up" },
    { name: "Tap: Create Account" },
    { name: "Screen_View: Email Verification" },
    { name: "Tap: Verify Email" },
    { name: "Screen_View: Profile Setup" },
    { name: "Tap: Complete Profile" },
    { name: "Screen_View: Home" },
    { name: "Tap: First Purchase" },
    { name: "Exit" },
  ],
  links: [
    { source: "App_Launch", target: "Screen_View: Welcome", value: 12400 },
    { source: "Screen_View: Welcome", target: "Tap: Get Started", value: 10800 },
    { source: "Screen_View: Welcome", target: "Exit", value: 1600 },
    { source: "Tap: Get Started", target: "Screen_View: Sign Up", value: 9900 },
    { source: "Tap: Get Started", target: "Exit", value: 900 },
    { source: "Screen_View: Sign Up", target: "Tap: Create Account", value: 8500 },
    { source: "Screen_View: Sign Up", target: "Exit", value: 1400 },
    { source: "Tap: Create Account", target: "Screen_View: Email Verification", value: 7800 },
    { source: "Tap: Create Account", target: "Exit", value: 700 },
    { source: "Screen_View: Email Verification", target: "Tap: Verify Email", value: 6900 },
    { source: "Screen_View: Email Verification", target: "Exit", value: 900 },
    { source: "Tap: Verify Email", target: "Screen_View: Profile Setup", value: 6200 },
    { source: "Tap: Verify Email", target: "Exit", value: 700 },
    { source: "Screen_View: Profile Setup", target: "Tap: Complete Profile", value: 5600 },
    { source: "Screen_View: Profile Setup", target: "Exit", value: 600 },
    { source: "Tap: Complete Profile", target: "Screen_View: Home", value: 5100 },
    { source: "Tap: Complete Profile", target: "Exit", value: 500 },
    { source: "Screen_View: Home", target: "Tap: First Purchase", value: 2800 },
    { source: "Screen_View: Home", target: "Exit", value: 2300 },
    { source: "Tap: First Purchase", target: "Exit", value: 2800 },
  ],
};

const MOCK_GROUPED_DATA: Record<string, any> = {
  OS: [
    {
      groupValue: "iOS",
      steps: [
        {
          stepName: "Screen_View: Home",
          count: 8200,
          conversionRate: 100,
          dropoffRate: 0,
          medianTimeToStep: null,
        },
        {
          stepName: "Screen_View: Product Detail",
          count: 5900,
          conversionRate: 72.0,
          dropoffRate: 28.0,
          medianTimeToStep: 3.8,
        },
        {
          stepName: "Tap: Add to Cart",
          count: 4300,
          conversionRate: 52.4,
          dropoffRate: 27.1,
          medianTimeToStep: 11.2,
        },
        {
          stepName: "Tap: Checkout",
          count: 3300,
          conversionRate: 40.2,
          dropoffRate: 23.3,
          medianTimeToStep: 7.9,
        },
        {
          stepName: "Tap: Place Order",
          count: 3000,
          conversionRate: 36.6,
          dropoffRate: 9.1,
          medianTimeToStep: 42.1,
        },
      ],
    },
    {
      groupValue: "Android",
      steps: [
        {
          stepName: "Screen_View: Home",
          count: 6000,
          conversionRate: 100,
          dropoffRate: 0,
          medianTimeToStep: null,
        },
        {
          stepName: "Screen_View: Product Detail",
          count: 3780,
          conversionRate: 63.0,
          dropoffRate: 37.0,
          medianTimeToStep: 4.8,
        },
        {
          stepName: "Tap: Add to Cart",
          count: 2540,
          conversionRate: 42.3,
          dropoffRate: 32.8,
          medianTimeToStep: 14.9,
        },
        {
          stepName: "Tap: Checkout",
          count: 1800,
          conversionRate: 30.0,
          dropoffRate: 29.1,
          medianTimeToStep: 9.6,
        },
        {
          stepName: "Tap: Place Order",
          count: 1600,
          conversionRate: 26.7,
          dropoffRate: 11.1,
          medianTimeToStep: 50.8,
        },
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
  "Tap: Sign Up",
  "Tap: Share",
  "App_Launch",
  "App_Opened",
  "Screen_View: Onboarding",
  "App_Background",
  "App_Crash",
  "Push_Opened",
  "Deep_Link_Opened",
];

const MOCK_FUNNEL_FILTER_OPTIONS: Record<string, string[]> = {
  "OS Name": ["iOS", "Android"],
  "OS Version": ["17.4.1", "17.3", "16.6", "14.0", "13.0"],
  "App Version": ["4.2.1", "4.2.0", "4.1.9", "4.1.8", "4.1.7"],
};

/** Default expiry for mock funnels/journeys (one year from when the module loads). */
const MOCK_EXPIRY_ONE_YEAR_FROM_NOW = (() => {
  const d = new Date();
  d.setFullYear(d.getFullYear() + 1);
  return d.toISOString();
})();

/** Saved funnels & journeys listing (mock only; TODO: replace with real API). */
const MOCK_FUNNELS_JOURNEYS_ALL: Array<{
  id: string;
  name: string;
  kind: "FUNNEL" | "JOURNEY";
  status: "ACTIVE" | "STOPPED" | "CREATING" | "UPDATING" | "COMPLETED";
  createdBy: string;
  lastUpdatedAt: string;
  tags: string[];
  expiryDate?: string;
  description?: string;
  funnelType?: "ORDERED" | "UNORDERED";
  rollingType?: "RECURRING" | "ONCE";
  windowSeconds?: number;
  filters?: any[];
  steps?: any[];
  timeRange?: { start: string; end: string };
  anchorEvent?: string;
  direction?: "forward" | "reverse";
  depth?: number;
}> = [
  {
    id: "fj-1",
    name: "Checkout conversion",
    kind: "FUNNEL",
    status: "ACTIVE",
    createdBy: "alice@example.com",
    lastUpdatedAt: "2026-03-20T14:22:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["checkout", "revenue"],
    funnelType: "ORDERED",
    filters: [
      { field: "OS Name", value: "iOS" },
      { field: "App Version", value: "4.2.1" },
    ],
    steps: [
      { eventName: "Screen_View: Home" },
      { eventName: "Screen_View: Product Detail" },
      { eventName: "Tap: Add to Cart" },
      { eventName: "Tap: Checkout" },
      { eventName: "Tap: Place Order" },
    ],
  },
  {
    id: "fj-2",
    name: "Onboarding drop-off",
    kind: "FUNNEL",
    status: "ACTIVE",
    createdBy: "bob@example.com",
    lastUpdatedAt: "2026-03-19T09:10:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["onboarding"],
    funnelType: "UNORDERED",
    rollingType: "RECURRING",
    windowSeconds: 86400,
    timeRange: {
      start: "2026-03-17T00:00:00Z",
      end: "2026-03-24T23:59:59Z",
    },
    steps: [
      { eventName: "App_Launch" },
      { eventName: "Screen_View: Onboarding" },
      { eventName: "Tap: Sign Up" },
    ],
  },
  {
    id: "fj-3",
    name: "Search to PDP",
    kind: "FUNNEL",
    status: "STOPPED",
    createdBy: "alice@example.com",
    lastUpdatedAt: "2026-03-10T18:45:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["search", "product"],
    funnelType: "ORDERED",
    rollingType: "RECURRING",
    windowSeconds: 172800,
    timeRange: {
      start: "2026-03-01T00:00:00Z",
      end: "2026-03-14T23:59:59Z",
    },
    steps: [
      { eventName: "Screen_View: Search" },
      { eventName: "Screen_View: Product Detail" },
    ],
  },
  {
    id: "fj-4",
    name: "Post-login paths",
    kind: "JOURNEY",
    status: "ACTIVE",
    createdBy: "carol@example.com",
    lastUpdatedAt: "2026-03-21T11:30:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["auth", "onboarding"],
    filters: [{ field: "OS Name", value: "Android" }],
    anchorEvent: "Tap: Sign Up",
    direction: "forward",
    depth: 5,
  },
  {
    id: "fj-5",
    name: "Cart abandonment",
    kind: "JOURNEY",
    status: "STOPPED",
    createdBy: "bob@example.com",
    lastUpdatedAt: "2026-02-28T08:00:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["checkout", "cart"],
    anchorEvent: "Tap: Add to Cart",
    direction: "forward",
    depth: 4,
  },
  {
    id: "fj-6",
    name: "Deep link attribution",
    kind: "FUNNEL",
    status: "ACTIVE",
    createdBy: "dev@example.com",
    lastUpdatedAt: "2026-03-22T16:05:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["marketing"],
    funnelType: "ORDERED",
    rollingType: "RECURRING",
    windowSeconds: 3600,
    timeRange: {
      start: "2026-03-17T00:00:00Z",
      end: "2026-03-24T23:59:59Z",
    },
    steps: [
      { eventName: "Deep_Link_Opened" },
      { eventName: "Screen_View: Home" },
    ],
  },
  {
    id: "funnel-payment-001",
    name: "Payment Flow Conversion",
    kind: "FUNNEL",
    status: "ACTIVE",
    createdBy: "sarah@example.com",
    lastUpdatedAt: "2026-03-21T14:30:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["payment", "conversion", "critical"],
    description: "Tracks user conversion through the payment process including checkout and order completion.",
    funnelType: "ORDERED",
    rollingType: "RECURRING",
    windowSeconds: 3600,
    filters: [
      { field: "OS Name", value: "iOS" },
      { field: "App Version", value: "4.2.1" },
    ],
    steps: [
      { eventName: "Screen_View: Cart" },
      { eventName: "Tap: Checkout" },
      { eventName: "Screen_View: Payment" },
      { eventName: "Tap: Enter Payment Details" },
      { eventName: "Tap: Place Order" },
      { eventName: "Screen_View: Order Confirmation" },
    ],
    timeRange: {
      start: "2026-03-17T00:00:00Z",
      end: "2026-03-24T23:59:59Z",
    },
  },
  {
    id: "journey-onboarding-001", 
    name: "User Onboarding Journey",
    kind: "JOURNEY",
    status: "ACTIVE",
    createdBy: "alex@example.com",
    lastUpdatedAt: "2026-03-17T09:15:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["onboarding", "ux", "retention"],
    description: "Maps the complete user journey from app launch to account creation and first purchase.",
    anchorEvent: "App_Launch",
    direction: "forward",
    depth: 5,
    rollingType: "RECURRING",
    filters: [
      { field: "OS Name", value: "Android" },
      { field: "App Version", value: "4.2.0" },
    ],
    timeRange: {
      start: "2026-03-17T00:00:00Z",
      end: "2026-03-24T23:59:59Z",
    },
  },
  {
    id: "funnel-completed-001",
    name: "Seasonal promo checkout",
    kind: "FUNNEL",
    status: "COMPLETED",
    createdBy: "ops@example.com",
    lastUpdatedAt: "2026-03-15T12:00:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["promo", "checkout", "archived"],
    description: "Holiday campaign funnel — run completed; data is read-only.",
    funnelType: "ORDERED",
    rollingType: "ONCE",
    windowSeconds: 86400,
    filters: [{ field: "OS Name", value: "iOS" }],
    steps: [
      { eventName: "Screen_View: Home" },
      { eventName: "Screen_View: Product Detail" },
      { eventName: "Tap: Add to Cart" },
      { eventName: "Tap: Checkout" },
      { eventName: "Tap: Place Order" },
    ],
    timeRange: {
      start: "2026-02-01T00:00:00Z",
      end: "2026-02-28T23:59:59Z",
    },
  },
  {
    id: "journey-completed-001",
    name: "App launch exploration (completed)",
    kind: "JOURNEY",
    status: "COMPLETED",
    createdBy: "ops@example.com",
    lastUpdatedAt: "2026-03-10T09:00:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["launch", "archived"],
    description: "Exploratory journey for a past release — completed; no longer updating.",
    anchorEvent: "App_Launch",
    direction: "forward",
    depth: 5,
    rollingType: "RECURRING",
    filters: [{ field: "App Version", value: "4.2.1" }],
    timeRange: {
      start: "2026-03-01T00:00:00Z",
      end: "2026-03-14T23:59:59Z",
    },
  },
  {
    id: "fj-7",
    name: "New feature adoption",
    kind: "FUNNEL",
    status: "CREATING",
    createdBy: "dev@example.com",
    lastUpdatedAt: "2026-03-24T10:00:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["feature"],
    funnelType: "ORDERED",
    steps: [{ eventName: "App_Opened" }, { eventName: "Tap: New Feature" }],
  },
  {
    id: "fj-8",
    name: "Onboarding journey",
    kind: "JOURNEY",
    status: "CREATING",
    createdBy: "alice@example.com",
    lastUpdatedAt: "2026-03-24T11:00:00Z",
    expiryDate: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["onboarding"],
    anchorEvent: "App_Opened",
    direction: "forward",
    depth: 5,
  },
];

function mockFunnelsJourneysList(request: MockRequest): MockResponse {
  let url: URL;
  try {
    url = new URL(request.url);
  } catch {
    url = new URL(request.url, "http://localhost");
  }
  const params = url.searchParams;
  const kindParam = params.get("kind") as "FUNNEL" | "JOURNEY" | null;
  const search = (params.get("search") || "").trim().toLowerCase();
  const status = params.get("status") as
    | "ACTIVE"
    | "STOPPED"
    | "CREATING"
    | "UPDATING"
    | "COMPLETED"
    | null;
  const createdByRaw = params.get("createdBy");
  const createdByFilters = createdByRaw
    ? createdByRaw
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
    : [];
  const tagsRaw = params.get("tags");
  const tagFilters = tagsRaw
    ? tagsRaw
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
    : [];
  const funnelType = params.get("funnelType") as "ORDERED" | "UNORDERED" | null;

  let pool = [...MOCK_FUNNELS_JOURNEYS_ALL];
  if (kindParam === "FUNNEL" || kindParam === "JOURNEY") {
    pool = pool.filter((row) => row.kind === kindParam);
  }

  const filterOptions = {
    creators: Array.from(new Set(pool.map((i) => i.createdBy))).sort(),
    tags: Array.from(new Set(pool.flatMap((i) => i.tags))).sort(),
  };

  let items = [...pool];

  if (search) {
    items = items.filter((row) => row.name.toLowerCase().includes(search));
  }
  if (
    status === "ACTIVE" ||
    status === "STOPPED" ||
    status === "CREATING" ||
    status === "UPDATING" ||
    status === "COMPLETED"
  ) {
    items = items.filter((row) => row.status === status);
  }
  if (createdByFilters.length) {
    items = items.filter((row) => createdByFilters.includes(row.createdBy));
  }
  if (tagFilters.length) {
    items = items.filter((row) => tagFilters.some((t) => row.tags.includes(t)));
  }
  if (funnelType === "ORDERED" || funnelType === "UNORDERED") {
    items = items.filter(
      (row) => row.kind === "FUNNEL" && row.funnelType === funnelType,
    );
  }

  const totalCount = items.length;
  const pageSizeRaw = params.get("pageSize");
  const pageRaw = params.get("page");
  let pageSize = parseInt(pageSizeRaw || "10", 10);
  if (!Number.isFinite(pageSize) || pageSize < 1) pageSize = 10;
  pageSize = Math.min(100, pageSize);
  let page = parseInt(pageRaw || "1", 10);
  if (!Number.isFinite(page) || page < 1) page = 1;

  const totalPages =
    totalCount === 0 ? 1 : Math.max(1, Math.ceil(totalCount / pageSize));
  if (page > totalPages) page = totalPages;

  const start = (page - 1) * pageSize;
  const paginatedItems = items.slice(start, start + pageSize);

  return {
    data: {
      items: paginatedItems,
      filterOptions,
      totalCount,
      page,
      pageSize,
      totalPages,
    },
    status: 200,
  };
}

function mockFunnelJourneyDetail(id: string): MockResponse {
  const row = MOCK_FUNNELS_JOURNEYS_ALL.find((r) => r.id === id);
  if (!row) {
    return {
      data: null,
      status: 404,
      error: {
        code: "NOT_FOUND",
        message: "Funnel or journey not found",
        cause: `No resource with id ${id}`,
      },
    };
  }

  const createdAt =
    row.kind === "FUNNEL" ? "2026-01-15T10:00:00Z" : "2026-02-01T12:00:00Z";
  const description =
    row.kind === "FUNNEL"
      ? "Conversion funnel across key product events. Edit steps and run analysis from the builder when the full editor is connected."
      : "Exploratory journey map for navigation paths after this anchor event. Open the journey explorer to adjust the root event and direction.";

  return {
    data: {
      ...row,
      description,
      createdAt,
    },
    status: 200,
  };
}

const MOCK_TAGS = [
  "checkout",
  "revenue",
  "onboarding",
  "search",
  "product",
  "auth",
  "cart",
  "marketing",
  "feature",
];

export function handleFunnelEndpoints(
  pathname: string,
  method: string,
  request: MockRequest,
): MockResponse {
  if (pathname.includes("/v1/funnels-journeys") && method === "POST") {
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }
    const newId = `fj-${Date.now()}`;
    const newItem = {
      id: newId,
      name: body.name || "Untitled",
      description: body.description || "",
      kind: body.kind || "FUNNEL",
      status: "CREATING" as const,
      createdBy: "dev@example.com",
      createdAt: new Date().toISOString(),
      lastUpdatedAt: new Date().toISOString(),
      tags: body.tags || [],
      funnelType: body.funnelType,
      filters: body.filters || [],
      expiryDate: body.expiryDate ?? MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
      rollingType: body.rollingType,
      steps: body.steps,
      timeRange: body.timeRange,
      windowSeconds: body.windowSeconds,
      anchorEvent: body.anchorEvent,
      direction: body.direction,
      depth: body.depth,
    };
    MOCK_FUNNELS_JOURNEYS_ALL.unshift(newItem);
    return { data: newItem, status: 201 };
  }

  if (pathname.includes("/v1/funnels-journeys/") && method === "PUT") {
    const id = pathname.split("/").pop();
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }

    const index = MOCK_FUNNELS_JOURNEYS_ALL.findIndex((item) => item.id === id);
    if (index !== -1) {
      MOCK_FUNNELS_JOURNEYS_ALL[index] = {
        ...MOCK_FUNNELS_JOURNEYS_ALL[index],
        ...body,
        status: "UPDATING" as const,
        lastUpdatedAt: new Date().toISOString(),
      };
      return { data: MOCK_FUNNELS_JOURNEYS_ALL[index], status: 200 };
    }
    return { data: null, status: 404 };
  }

  if (pathname.includes("/v1/funnels-journeys/") && method === "PUT") {
    const id = pathname.split("/").pop();
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }

    const index = MOCK_FUNNELS_JOURNEYS_ALL.findIndex((item) => item.id === id);
    if (index !== -1) {
      MOCK_FUNNELS_JOURNEYS_ALL[index] = {
        ...MOCK_FUNNELS_JOURNEYS_ALL[index],
        ...body,
        status: "UPDATING" as const,
        lastUpdatedAt: new Date().toISOString(),
      };
      return { data: MOCK_FUNNELS_JOURNEYS_ALL[index], status: 200 };
    }
    return { data: null, status: 404 };
  }

  if (pathname.includes("/v1/funnels-journeys") && method === "GET") {
    const pathOnly = pathname.split("?")[0].replace(/\/$/, "");
    if (pathOnly.endsWith("/v1/funnels-journeys")) {
      return mockFunnelsJourneysList(request);
    }
    const marker = "/v1/funnels-journeys/";
    const markerIdx = pathOnly.lastIndexOf(marker);
    if (markerIdx >= 0) {
      const id = pathOnly.slice(markerIdx + marker.length);
      if (id && !id.includes("/")) {
        return mockFunnelJourneyDetail(id);
      }
    }
    return mockFunnelsJourneysList(request);
  }

  if (pathname.includes("/v1/funnel/analyze") && method === "POST") {
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }

    const steps = body.steps || [];
    const hasCartStep = steps.some(
      (step: any) => step.eventName === "Screen_View: Cart",
    );
    const hasPaymentStep = steps.some(
      (step: any) => step.eventName === "Screen_View: Payment",
    );

    if (hasCartStep && hasPaymentStep) {
      return { data: MOCK_PAYMENT_FUNNEL_ANALYZE_RESPONSE, status: 200 };
    }

    const { analyze } = buildMockFunnelAnalyzeAndTrendFromSteps(body);
    return { data: analyze, status: 200 };
  }

  if (pathname.includes("/v1/funnel/health") && method === "POST") {
    return { data: MOCK_FUNNEL_HEALTH_RESPONSE, status: 200 };
  }

  if (pathname.includes("/v1/funnel/sessions") && method === "POST") {
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }
    const resp = { ...MOCK_FUNNEL_SESSIONS_RESPONSE };
    if (body.stepLevel) resp.stepLevel = body.stepLevel;
    return { data: resp, status: 200 };
  }

  if (pathname.includes("/v1/funnel/trend") && method === "POST") {
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }

    const steps = body.steps || [];
    const hasCartStep = steps.some(
      (step: any) => step.eventName === "Screen_View: Cart",
    );
    const hasPaymentStep = steps.some(
      (step: any) => step.eventName === "Screen_View: Payment",
    );

    if (hasCartStep && hasPaymentStep) {
      return { data: MOCK_PAYMENT_FUNNEL_CONVERSION_TREND, status: 200 };
    }

    const { trend } = buildMockFunnelAnalyzeAndTrendFromSteps(body);
    return { data: trend, status: 200 };
  }

  if (pathname.includes("/v1/funnel/grouped") && method === "POST") {
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }
    const groupBy = body.groupBy || "OS";
    return { data: { groups: MOCK_GROUPED_DATA[groupBy] || [] }, status: 200 };
  }

  if (pathname.includes("/v1/funnel/events") && method === "GET") {
    return { data: { events: MOCK_FUNNEL_EVENTS }, status: 200 };
  }

  if (pathname.includes("/v1/funnel/filters") && method === "GET") {
    return { data: { filters: MOCK_FUNNEL_FILTER_OPTIONS }, status: 200 };
  }

  if (pathname.includes("/v1/tags") && method === "GET") {
    return { data: { tags: MOCK_TAGS }, status: 200 };
  }

  if (pathname.includes("/v1/journey/explore") && method === "POST") {
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }
    const direction = body.direction || "forward";
    const anchorEvent = body.anchorEvent || "";
    
    // Check if this is for the onboarding journey
    if (anchorEvent === "App_Launch" && direction === "forward") {
      return { data: MOCK_ONBOARDING_JOURNEY_RESPONSE, status: 200 };
    }
    
    const data =
      direction === "reverse" ? MOCK_JOURNEY_REVERSE : MOCK_JOURNEY_FORWARD;
    return { data, status: 200 };
  }

  return { data: { message: "Unknown funnel endpoint" }, status: 404 };
}
