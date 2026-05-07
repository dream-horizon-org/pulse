/**
 * Funnel Analysis & Journey Explorer Mock Responses
 */

import {MockRequest, MockResponse} from "../types";
import {API_ROUTES} from "../../constants";
import {FunnelMode, FunnelType, StepOrderType,} from "../../services/funnels.service";

/**
 * Single source of truth for funnel conversion KPIs in mocks.
 * Funnels & Journeys listing and funnel detail (analyze/trend) use these values when steps match.
 */
const MOCK_FUNNEL_CONVERSION_BY_ID: Record<
  string,
  { overallConversionRate: number; conversionTrend: number }
> = {
  "fj-1": { overallConversionRate: 32.4, conversionTrend: 2.1 },
  "fj-2": { overallConversionRate: 18.2, conversionTrend: -1.4 },
  "fj-3": { overallConversionRate: 62.3, conversionTrend: 0.7 },
  "fj-6": { overallConversionRate: 12.8, conversionTrend: -3.2 },
  "funnel-payment-001": { overallConversionRate: 46.3, conversionTrend: -1.8 },
  "funnel-completed-001": { overallConversionRate: 36.6, conversionTrend: 0.2 },
};

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
      conversionRate:
        MOCK_FUNNEL_CONVERSION_BY_ID["funnel-payment-001"]
          .overallConversionRate,
      dropoffRate: 4.3,
    },
  ],
  totalEnteredUsers: 8750,
  overallConversionRate:
    MOCK_FUNNEL_CONVERSION_BY_ID["funnel-payment-001"].overallConversionRate,
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
  totalConversionRate:
    MOCK_FUNNEL_CONVERSION_BY_ID["funnel-payment-001"].overallConversionRate,
  conversionTrend:
    MOCK_FUNNEL_CONVERSION_BY_ID["funnel-payment-001"].conversionTrend,
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
      i === 0 ? 0 : (1 - count / stepResults[i - 1].count) * 100;
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
    {
      source: "Screen_View: Welcome",
      target: "Tap: Get Started",
      value: 10800,
    },
    { source: "Screen_View: Welcome", target: "Exit", value: 1600 },
    { source: "Tap: Get Started", target: "Screen_View: Sign Up", value: 9900 },
    { source: "Tap: Get Started", target: "Exit", value: 900 },
    {
      source: "Screen_View: Sign Up",
      target: "Tap: Create Account",
      value: 8500,
    },
    { source: "Screen_View: Sign Up", target: "Exit", value: 1400 },
    {
      source: "Tap: Create Account",
      target: "Screen_View: Email Verification",
      value: 7800,
    },
    { source: "Tap: Create Account", target: "Exit", value: 700 },
    {
      source: "Screen_View: Email Verification",
      target: "Tap: Verify Email",
      value: 6900,
    },
    { source: "Screen_View: Email Verification", target: "Exit", value: 900 },
    {
      source: "Tap: Verify Email",
      target: "Screen_View: Profile Setup",
      value: 6200,
    },
    { source: "Tap: Verify Email", target: "Exit", value: 700 },
    {
      source: "Screen_View: Profile Setup",
      target: "Tap: Complete Profile",
      value: 5600,
    },
    { source: "Screen_View: Profile Setup", target: "Exit", value: 600 },
    {
      source: "Tap: Complete Profile",
      target: "Screen_View: Home",
      value: 5100,
    },
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
  "Screen_View: Payment",
  "Screen_View: Product Detail",
  "Screen_View: Profile",
  "Screen_View: Search",
  "Screen_View: Checkout",
  "Screen_View: Order Confirmation",
  "Screen_View: Settings",
  "Tap: Add to Cart",
  "Tap: Checkout",
  "Tap: Enter Payment Details",
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

/**
 * Mock filter keys returned by GET /v1/funnels/filters.
 * These are server-side keys; the UI maps them to display labels via FILTER_KEY_LABEL_MAP.
 */
const MOCK_FUNNEL_FILTER_KEYS: string[] = [
  "os_name",
  "os_version",
  "app_version",
];

/**
 * Mock values for each filter key returned by GET /v1/funnels/filters/{filterKey}/values.
 * Keyed by server filter key.
 */
const MOCK_FUNNEL_FILTER_VALUES: Record<string, string[]> = {
  os_name: ["iOS", "Android"],
  os_version: ["17.4.1", "17.3", "16.6", "14.0", "13.0"],
  app_version: ["4.2.1", "4.2.0", "4.1.9", "4.1.8", "4.1.7"],
};

/** Default expiry for mock funnels/journeys (one year from when the module loads). */
const MOCK_EXPIRY_ONE_YEAR_FROM_NOW = (() => {
  const d = new Date();
  d.setFullYear(d.getFullYear() + 1);
  return d.toISOString();
})();

/**
 * Internal canonical shape backing both listing and detail mock responses for funnels &
 * journeys. Mirrors the union of {@code FunnelDetail} ∪ {@code JourneyDetail} from
 * `services/funnels.service.ts` plus an internal `kind` discriminator. Listing handlers
 * project to {@code FunnelListItem}/{@code JourneyListItem}; detail handlers return the full
 * row plus injected {@code createdAt}/{@code description}/{@code funnelResults}/
 * {@code journeyResults}.
 *
 * Field-name conventions match prod:
 *   - Detail uses `expiry` (not `expiryDate`) for the AUTO refresh deadline.
 *   - `filters` is `FilterField[]` shape: `{ field, operator, value }`.
 *   - `mode` defaults to `UNIQUE_USERS` (the analysis grouping default on create).
 */
const MOCK_FUNNELS_JOURNEYS_ALL: Array<{
  id: string;
  name: string;
  kind: "FUNNEL" | "JOURNEY";
  status:
    | "ACTIVE"
    | "IN_PROGRESS"
    | "WARN"
    | "PENDING"
    | "FAILED"
    | "COMPLETED";
  createdBy: string;
  /** Optional explicit creation timestamp; synthesised by the detail projector when omitted. */
  createdAt?: string;
  updatedAt: string;
  tags: string[];
  /** AUTO funnels/journeys: ISO datetime when auto-refresh stops (canonical detail name). */
  expiry?: string;
  /** ONCE funnels/journeys: fixed analysis window. */
  startTime?: string;
  endTime?: string;
  description?: string;
  /** Funnel-only fields. */
  stepOrderType?: StepOrderType;
  funnelType?: FunnelType;
  windowSeconds?: number;
  steps?: Array<{ eventName: string }>;
  /** Listing-level conversion summary used by the funnels list view. */
  overallConversionRate?: number;
  conversionTrend?: number;
  /** Journey-only fields. */
  journeyType?: FunnelType;
  anchorEvent?: string;
  direction?: "START" | "END";
  depth?: number;
  /** Shared (detail-shape) fields. */
  mode?: FunnelMode;
  dateRangeDays?: number;
  filters?: Array<{
    field: string;
    operator: "EQ" | "NE" | "IN" | "NOT_IN";
    value: string | string[];
  }>;
  /**
   * @deprecated Older mock rows still set this for the analyze flow's display window.
   * Prod detail uses `startTime`/`endTime` (ONCE) or `dateRangeDays` (AUTO). Kept on the
   * internal row type so legacy fixtures compile, but never returned by the listing or
   * detail projectors.
   */
  timeRange?: { start: string; end: string };
}> = [
  {
    id: "fj-1",
    name: "Checkout conversion",
    kind: "FUNNEL",
    status: "ACTIVE",
    createdBy: "alice@example.com",
    updatedAt: "2026-03-20T14:22:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["checkout", "revenue"],
    stepOrderType: StepOrderType.ORDERED,
    filters: [
      { field: "OS Name", operator: "EQ", value: "iOS" },
      { field: "App Version", operator: "EQ", value: "4.2.1" },
    ],
    steps: [
      { eventName: "Screen_View: Home" },
      { eventName: "Screen_View: Product Detail" },
      { eventName: "Tap: Add to Cart" },
      { eventName: "Tap: Checkout" },
      { eventName: "Tap: Place Order" },
    ],
    ...MOCK_FUNNEL_CONVERSION_BY_ID["fj-1"],
  },
  {
    id: "fj-2",
    name: "Onboarding drop-off",
    kind: "FUNNEL",
    status: "ACTIVE",
    createdBy: "bob@example.com",
    updatedAt: "2026-03-19T09:10:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["onboarding"],
    stepOrderType: StepOrderType.UNORDERED,
    funnelType: FunnelType.AUTO,
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
    ...MOCK_FUNNEL_CONVERSION_BY_ID["fj-2"],
  },
  {
    id: "fj-3",
    name: "Search to PDP",
    kind: "FUNNEL",
    status: "WARN",
    createdBy: "alice@example.com",
    updatedAt: "2026-03-10T18:45:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["search", "product"],
    stepOrderType: StepOrderType.ORDERED,
    funnelType: FunnelType.AUTO,
    windowSeconds: 172800,
    timeRange: {
      start: "2026-03-01T00:00:00Z",
      end: "2026-03-14T23:59:59Z",
    },
    steps: [
      { eventName: "Screen_View: Search" },
      { eventName: "Screen_View: Product Detail" },
    ],
    ...MOCK_FUNNEL_CONVERSION_BY_ID["fj-3"],
  },
  {
    id: "fj-4",
    name: "Post-login paths",
    kind: "JOURNEY",
    status: "ACTIVE",
    createdBy: "carol@example.com",
    updatedAt: "2026-03-21T11:30:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["auth", "onboarding"],
    filters: [{ field: "OS Name", operator: "EQ", value: "Android" }],
    anchorEvent: "Tap: Sign Up",
    direction: "START",
    depth: 5,
  },
  {
    id: "fj-5",
    name: "Cart abandonment",
    kind: "JOURNEY",
    status: "FAILED",
    createdBy: "bob@example.com",
    updatedAt: "2026-02-28T08:00:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["checkout", "cart"],
    anchorEvent: "Tap: Add to Cart",
    direction: "START",
    depth: 4,
  },
  {
    id: "fj-6",
    name: "Deep link attribution",
    kind: "FUNNEL",
    status: "ACTIVE",
    createdBy: "dev@example.com",
    updatedAt: "2026-03-22T16:05:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["marketing"],
    stepOrderType: StepOrderType.ORDERED,
    funnelType: FunnelType.AUTO,
    windowSeconds: 3600,
    timeRange: {
      start: "2026-03-17T00:00:00Z",
      end: "2026-03-24T23:59:59Z",
    },
    steps: [
      { eventName: "Deep_Link_Opened" },
      { eventName: "Screen_View: Home" },
    ],
    ...MOCK_FUNNEL_CONVERSION_BY_ID["fj-6"],
  },
  {
    id: "funnel-payment-001",
    name: "Payment Flow Conversion",
    kind: "FUNNEL",
    status: "ACTIVE",
    createdBy: "sarah@example.com",
    updatedAt: "2026-03-21T14:30:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["payment", "conversion", "critical"],
    description:
      "Tracks user conversion through the payment process including checkout and order completion.",
    stepOrderType: StepOrderType.ORDERED,
    funnelType: FunnelType.AUTO,
    windowSeconds: 3600,
    filters: [
      { field: "OS Name", operator: "EQ", value: "iOS" },
      { field: "App Version", operator: "EQ", value: "4.2.1" },
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
    ...MOCK_FUNNEL_CONVERSION_BY_ID["funnel-payment-001"],
  },
  {
    id: "journey-onboarding-001",
    name: "User Onboarding Journey",
    kind: "JOURNEY",
    status: "ACTIVE",
    createdBy: "alex@example.com",
    updatedAt: "2026-03-17T09:15:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["onboarding", "ux", "retention"],
    description:
      "Maps the complete user journey from app launch to account creation and first purchase.",
    anchorEvent: "App_Launch",
    direction: "START",
    depth: 5,
    funnelType: FunnelType.AUTO,
    filters: [
      { field: "OS Name", operator: "EQ", value: "Android" },
      { field: "App Version", operator: "EQ", value: "4.2.0" },
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
    updatedAt: "2026-03-15T12:00:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["promo", "checkout", "archived"],
    description: "Holiday campaign funnel — run completed; data is read-only.",
    stepOrderType: StepOrderType.ORDERED,
    funnelType: FunnelType.ONCE,
    windowSeconds: 86400,
    filters: [{ field: "OS Name", operator: "EQ", value: "iOS" }],
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
    ...MOCK_FUNNEL_CONVERSION_BY_ID["funnel-completed-001"],
  },
  {
    id: "journey-completed-001",
    name: "App launch exploration (completed)",
    kind: "JOURNEY",
    status: "COMPLETED",
    createdBy: "ops@example.com",
    updatedAt: "2026-03-10T09:00:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["launch", "archived"],
    description:
      "Exploratory journey for a past release — completed; no longer updating.",
    anchorEvent: "App_Launch",
    direction: "START",
    depth: 5,
    funnelType: FunnelType.AUTO,
    filters: [{ field: "App Version", operator: "EQ", value: "4.2.1" }],
    timeRange: {
      start: "2026-03-01T00:00:00Z",
      end: "2026-03-14T23:59:59Z",
    },
  },
  {
    id: "fj-7",
    name: "New feature adoption",
    kind: "FUNNEL",
    status: "IN_PROGRESS",
    createdBy: "dev@example.com",
    updatedAt: "2026-03-24T10:00:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["feature"],
    stepOrderType: StepOrderType.ORDERED,
    steps: [{ eventName: "App_Opened" }, { eventName: "Tap: New Feature" }],
  },
  {
    id: "fj-8",
    name: "Onboarding journey",
    kind: "JOURNEY",
    status: "IN_PROGRESS",
    createdBy: "alice@example.com",
    updatedAt: "2026-03-24T11:00:00Z",
    expiry: MOCK_EXPIRY_ONE_YEAR_FROM_NOW,
    tags: ["onboarding"],
    anchorEvent: "App_Opened",
    direction: "START",
    depth: 5,
  },
];

type MockFunnelJourneyRow = (typeof MOCK_FUNNELS_JOURNEYS_ALL)[number];

/**
 * Projects an internal mock row to the {@code FunnelListItem} shape returned by
 * `GET /v1/funnels`. Strips detail-only fields (steps, filters, windowSeconds, …) so
 * mocks match the lean prod listing payload.
 */
function projectFunnelListItem(row: MockFunnelJourneyRow) {
  return {
    id: row.id,
    name: row.name,
    status: row.status,
    createdBy: row.createdBy,
    createdAt: row.createdAt ?? synthesizeCreatedAt(row.updatedAt),
    updatedAt: row.updatedAt,
    tags: row.tags,
    funnelType: row.funnelType ?? FunnelType.AUTO,
    stepOrderType: row.stepOrderType,
    overallConversionRate: row.overallConversionRate,
    conversionTrend: row.conversionTrend,
  };
}

/**
 * Projects an internal mock row to the {@code JourneyListItem} shape returned by
 * `GET /v1/journeys`. Lean: id/name/status/createdBy/updatedAt/tags/journeyType only.
 */
function projectJourneyListItem(row: MockFunnelJourneyRow) {
  return {
    id: row.id,
    name: row.name,
    status: row.status,
    createdBy: row.createdBy,
    createdAt: row.createdAt ?? synthesizeCreatedAt(row.updatedAt),
    updatedAt: row.updatedAt,
    tags: row.tags,
    journeyType: row.journeyType ?? row.funnelType ?? FunnelType.AUTO,
  };
}

/**
 * Mocks rows historically only carry `updatedAt`. To render a plausible "Created at"
 * column without backfilling every fixture, default to two days before the update
 * timestamp. Real prod listings always include both, so this only matters in mock mode.
 */
function synthesizeCreatedAt(updatedAt: string): string {
  const t = new Date(updatedAt).getTime();
  if (Number.isNaN(t)) return updatedAt;
  return new Date(t - 2 * 24 * 60 * 60 * 1000).toISOString();
}

/** Default `funnelResults` payload for funnel detail when no precomputed shape exists. */
function buildFunnelResultsForRow(row: MockFunnelJourneyRow) {
  if (!row.steps?.length) return undefined;
  if (row.id === "funnel-payment-001") {
    return {
      steps: MOCK_PAYMENT_FUNNEL_ANALYZE_RESPONSE.steps,
      overallConversionRate:
        MOCK_PAYMENT_FUNNEL_ANALYZE_RESPONSE.overallConversionRate,
    };
  }
  const built = buildMockFunnelAnalyzeAndTrendFromSteps({ steps: row.steps });
  if (
    row.overallConversionRate != null &&
    row.conversionTrend != null &&
    built.analyze.steps.length > 0
  ) {
    built.analyze.steps[built.analyze.steps.length - 1].conversionRate =
      row.overallConversionRate;
    built.analyze.overallConversionRate = row.overallConversionRate;
  }
  return {
    steps: built.analyze.steps,
    overallConversionRate: built.analyze.overallConversionRate,
  };
}

/** Default `journeyResults` payload for journey detail. */
function buildJourneyResultsForRow(row: MockFunnelJourneyRow) {
  if (row.anchorEvent === "App_Launch" && row.direction === "START") {
    return MOCK_ONBOARDING_JOURNEY_RESPONSE;
  }
  return row.direction === "END" ? MOCK_JOURNEY_REVERSE : MOCK_JOURNEY_FORWARD;
}

/**
 * Projects an internal mock row to the {@code FunnelDetail} shape returned by
 * `GET /v1/funnels/:id`. Adds prod-canonical defaults (mode, dateRangeDays,
 * windowSeconds, funnelType, stepOrderType, createdAt, description, funnelResults).
 */
function projectFunnelDetail(row: MockFunnelJourneyRow) {
  // Honor an explicit createdAt on the mock row when set; otherwise synthesise it from
  // updatedAt (so the detail page matches whatever the listing column shows) and fall
  // back to a fixed seed date for fixtures that have neither.
  const createdAt =
    row.createdAt ??
    (row.updatedAt
      ? synthesizeCreatedAt(row.updatedAt)
      : "2026-01-15T10:00:00Z");
  return {
    id: row.id,
    name: row.name,
    description:
      row.description ??
      "Conversion funnel across key product events. Edit steps and run analysis from the builder when the full editor is connected.",
    status: row.status,
    funnelType: row.funnelType ?? FunnelType.AUTO,
    stepOrderType: row.stepOrderType ?? StepOrderType.ORDERED,
    steps: row.steps ?? [],
    filters: row.filters ?? [],
    windowSeconds: row.windowSeconds ?? 86400,
    mode: row.mode ?? FunnelMode.UNIQUE_USERS,
    dateRangeDays: row.dateRangeDays ?? 7,
    startTime: row.startTime,
    endTime: row.endTime,
    expiry: row.expiry,
    createdAt,
    updatedAt: row.updatedAt,
    createdBy: row.createdBy,
    tags: row.tags,
    funnelResults: buildFunnelResultsForRow(row),
    // Surface listing-level conversion summary on the detail too so the detail
    // page's "+X% from last week" matches the listing's trend chip.
    overallConversionRate: row.overallConversionRate,
    conversionTrend: row.conversionTrend,
  };
}

/**
 * Projects an internal mock row to the {@code JourneyDetail} shape returned by
 * `GET /v1/journeys/:id`. Adds prod defaults (mode, dateRangeDays, journeyType,
 * createdAt, description, journeyResults).
 */
function projectJourneyDetail(row: MockFunnelJourneyRow) {
  const createdAt =
    row.createdAt ??
    (row.updatedAt
      ? synthesizeCreatedAt(row.updatedAt)
      : "2026-02-01T12:00:00Z");
  return {
    id: row.id,
    name: row.name,
    description:
      row.description ??
      "Exploratory journey map for navigation paths after this anchor event. Open the journey explorer to adjust the root event and direction.",
    status: row.status,
    anchorEvent: row.anchorEvent ?? "",
    direction: row.direction ?? "START",
    depth: row.depth ?? 5,
    mode: row.mode ?? FunnelMode.UNIQUE_USERS,
    journeyType: row.journeyType ?? row.funnelType ?? FunnelType.AUTO,
    filters: row.filters ?? [],
    startTime: row.startTime,
    endTime: row.endTime,
    expiry: row.expiry,
    dateRangeDays: row.dateRangeDays ?? 7,
    createdAt,
    updatedAt: row.updatedAt,
    createdBy: row.createdBy,
    tags: row.tags,
    journeyResults: buildJourneyResultsForRow(row),
  };
}

function normalizeFunnelStepSignature(
  steps: Array<{ eventName?: string } | undefined> | undefined,
): string {
  return (steps || [])
    .map((s) => (s?.eventName || "").trim())
    .filter(Boolean)
    .join("\0");
}

/** Match saved mock funnel by ordered step event names (detail analyze/trend uses same steps as listing). */
function findMockFunnelRowBySteps(
  steps: Array<{ eventName?: string }>,
): (typeof MOCK_FUNNELS_JOURNEYS_ALL)[number] | null {
  const sig = normalizeFunnelStepSignature(steps);
  if (!sig) return null;
  for (const row of MOCK_FUNNELS_JOURNEYS_ALL) {
    if (row.kind !== "FUNNEL" || !row.steps?.length) continue;
    if (
      normalizeFunnelStepSignature(
        row.steps as Array<{ eventName?: string }>,
      ) === sig
    ) {
      return row;
    }
  }
  return null;
}

function applyListingMetricsToFunnelAnalyzeAndTrend(
  analyze: {
    steps: Array<{ conversionRate: number }>;
    overallConversionRate: number;
  },
  trend: { totalConversionRate: number; conversionTrend: number },
  metrics: { overallConversionRate: number; conversionTrend: number },
) {
  const rate = metrics.overallConversionRate;
  analyze.overallConversionRate = rate;
  if (analyze.steps.length > 0) {
    analyze.steps[analyze.steps.length - 1].conversionRate = rate;
  }
  trend.totalConversionRate = rate;
  trend.conversionTrend = metrics.conversionTrend;
}

/** Listing metrics + detail analyze/trend share values via `MOCK_FUNNEL_CONVERSION_BY_ID` / row match. */
function getMockFunnelAnalyzeAndTrendFromBody(body: {
  steps?: Array<{ eventName?: string }>;
}): ReturnType<typeof buildMockFunnelAnalyzeAndTrendFromSteps> {
  const steps = body.steps || [];
  const hasCartStep = steps.some(
    (s: { eventName?: string }) => s.eventName === "Screen_View: Cart",
  );
  const hasPaymentStep = steps.some(
    (s: { eventName?: string }) => s.eventName === "Screen_View: Payment",
  );

  if (hasCartStep && hasPaymentStep) {
    return {
      analyze: MOCK_PAYMENT_FUNNEL_ANALYZE_RESPONSE,
      trend: MOCK_PAYMENT_FUNNEL_CONVERSION_TREND,
    };
  }

  const built = buildMockFunnelAnalyzeAndTrendFromSteps(body);
  const matched = findMockFunnelRowBySteps(steps);
  if (
    matched &&
    matched.kind === "FUNNEL" &&
    matched.overallConversionRate != null &&
    matched.conversionTrend != null
  ) {
    applyListingMetricsToFunnelAnalyzeAndTrend(built.analyze, built.trend, {
      overallConversionRate: matched.overallConversionRate,
      conversionTrend: matched.conversionTrend,
    });
  }
  return built;
}

/** Shared listing logic for saved funnels or journeys (mock). */
function mockResourceListing(
  request: MockRequest,
  forcedKind?: "FUNNEL" | "JOURNEY",
): MockResponse {
  let url: URL;
  try {
    url = new URL(request.url);
  } catch {
    url = new URL(request.url, "http://localhost");
  }
  const params = url.searchParams;
  const kindParam =
    forcedKind ?? (params.get("kind") as "FUNNEL" | "JOURNEY" | null);
  const search = (params.get("search") || "").trim().toLowerCase();
  const status = params.get("status") as
    | "ACTIVE"
    | "IN_PROGRESS"
    | "WARN"
    | "PENDING"
    | "FAILED"
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
  const stepOrderType = params.get("stepOrderType") as StepOrderType | null;

  let pool = [...MOCK_FUNNELS_JOURNEYS_ALL];
  if (kindParam === "FUNNEL" || kindParam === "JOURNEY") {
    pool = pool.filter((row) => row.kind === kindParam);
  }

  let items = [...pool];

  if (search) {
    items = items.filter((row) => row.name.toLowerCase().includes(search));
  }
  if (
    status === "ACTIVE" ||
    status === "IN_PROGRESS" ||
    status === "WARN" ||
    status === "PENDING" ||
    status === "FAILED" ||
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
  if (
    stepOrderType === StepOrderType.ORDERED ||
    stepOrderType === StepOrderType.UNORDERED
  ) {
    items = items.filter(
      (row) => row.kind === "FUNNEL" && row.stepOrderType === stepOrderType,
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
  const pageRows = items.slice(start, start + pageSize);
  // Project to the lean prod listing shape (FunnelListItem / JourneyListItem).
  // Detail-only fields (steps, filters, windowSeconds, anchorEvent, depth, …)
  // are intentionally stripped to mirror what the real /v1/funnels and /v1/journeys
  // endpoints return.
  const paginatedItems = pageRows.map((row) =>
    row.kind === "FUNNEL"
      ? projectFunnelListItem(row)
      : projectJourneyListItem(row),
  );

  // filterOptions: distinct creators + tags across the FILTERED pool (so the
  // sidebar's facets reflect what's actually selectable). Keeps parity with
  // ListFilterOptions returned by the prod listing endpoint.
  const creators = Array.from(new Set(pool.map((r) => r.createdBy))).sort();
  const tags = Array.from(new Set(pool.flatMap((r) => r.tags))).sort();

  return {
    data: {
      items: paginatedItems,
      totalCount,
      page,
      pageSize,
      totalPages,
      filterOptions: { creators, tags },
    },
    status: 200,
  };
}

function mockSavedResourceDetail(
  id: string,
  expectedKind?: "FUNNEL" | "JOURNEY",
): MockResponse {
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
  if (expectedKind && row.kind !== expectedKind) {
    return {
      data: null,
      status: 404,
      error: {
        code: "NOT_FOUND",
        message:
          expectedKind === "FUNNEL"
            ? "Not a funnel resource"
            : "Not a journey resource",
        cause: `id ${id} is not a ${expectedKind}`,
      },
    };
  }

  // Project to the prod detail shape (FunnelDetail / JourneyDetail). The detail
  // projector fills in canonical defaults (mode, dateRangeDays, windowSeconds, …)
  // and attaches funnelResults / journeyResults so the visualization renders
  // without requiring a separate analyze call.
  const data =
    row.kind === "FUNNEL"
      ? projectFunnelDetail(row)
      : projectJourneyDetail(row);
  return { data, status: 200 };
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

function mockFunnelListing(request: MockRequest): MockResponse {
  return mockResourceListing(request, "FUNNEL");
}

function mockJourneyListing(request: MockRequest): MockResponse {
  return mockResourceListing(request, "JOURNEY");
}

/**
 * Mocks the cascading `DELETE /v1/funnels/:id` and `DELETE /v1/journeys/:id`.
 *
 * <p>For the in-memory mock store this just removes the row from
 * {@code MOCK_FUNNELS_JOURNEYS_ALL}. The "and its tags / analytics_jobs /
 * funnel_results" cleanup is server-side; mocks have no separate tag/job/results
 * stores to clean up. 404 when the id is missing or the kind doesn't match.
 */
function mockCascadeDelete(
  id: string,
  expectedKind: "FUNNEL" | "JOURNEY",
): MockResponse {
  const index = MOCK_FUNNELS_JOURNEYS_ALL.findIndex((r) => r.id === id);
  if (index === -1) {
    return {
      data: null,
      status: 404,
      error: {
        code: "NOT_FOUND",
        message:
          expectedKind === "FUNNEL" ? "Funnel not found" : "Journey not found",
        cause: `No ${expectedKind === "FUNNEL" ? "funnel" : "journey"} with id ${id}`,
      },
    };
  }
  if (MOCK_FUNNELS_JOURNEYS_ALL[index].kind !== expectedKind) {
    return { data: null, status: 404 };
  }
  MOCK_FUNNELS_JOURNEYS_ALL.splice(index, 1);
  return { data: "Success", status: 200 };
}

/**
 * Mocks `POST /v1/funnels/:id/stop` and `POST /v1/journeys/:id/stop`.
 *
 * <p>Mirrors the backend's new STOP_AUTO behavior: sets {@code expiry = NOW()} on the row
 * but leaves {@code funnelType}/{@code journeyType} unchanged. The listing's status is
 * recomputed by the backend from `(type, expiry, latest_job)` — for the mock we just flip
 * status to COMPLETED so the listing repaints. Idempotent: a re-stop is a no-op success.
 */
function mockStopAuto(
  id: string,
  expectedKind: "FUNNEL" | "JOURNEY",
): MockResponse {
  const index = MOCK_FUNNELS_JOURNEYS_ALL.findIndex((r) => r.id === id);
  if (index === -1) {
    return {
      data: null,
      status: 404,
      error: {
        code: "NOT_FOUND",
        message:
          expectedKind === "FUNNEL" ? "Funnel not found" : "Journey not found",
        cause: `No ${expectedKind === "FUNNEL" ? "funnel" : "journey"} with id ${id}`,
      },
    };
  }
  if (MOCK_FUNNELS_JOURNEYS_ALL[index].kind !== expectedKind) {
    return { data: null, status: 404 };
  }
  const row = MOCK_FUNNELS_JOURNEYS_ALL[index];
  const now = new Date().toISOString();
  // Set expiry to now; keep funnelType/journeyType as-is so the listing still shows
  // "AUTO" — the badge flips to COMPLETED via the (type, expiry, latest_job) mapping.
  const updated: MockFunnelJourneyRow = {
    ...row,
    expiry: now,
    status: "COMPLETED",
    updatedAt: now,
  };
  MOCK_FUNNELS_JOURNEYS_ALL[index] = updated;
  return { data: "Success", status: 200 };
}

function mockPostCreateFunnelOrJourney(
  request: MockRequest,
  kind: "FUNNEL" | "JOURNEY",
): MockResponse {
  let body: any = {};
  try {
    body = JSON.parse(request.body || "{}");
  } catch {
    /* ignore */
  }
  const newId = `fj-${Date.now()}`;
  // Funnel CREATE body uses `expiryDate` (CreateFunnelRequestBody); journey CREATE
  // body uses `expiry` (CreateJourneyRequestBody). Normalize both to the canonical
  // detail field name `expiry`. Detail projector handles defaults.
  const expiry =
    body.expiry ?? body.expiryDate ?? MOCK_EXPIRY_ONE_YEAR_FROM_NOW;
  const newItem: MockFunnelJourneyRow = {
    id: newId,
    kind,
    name: body.name || "Untitled",
    description: body.description || "",
    status: "IN_PROGRESS",
    createdBy: "dev@example.com",
    updatedAt: new Date().toISOString(),
    tags: body.tags || [],
    expiry,
    startTime: body.startTime,
    endTime: body.endTime,
    mode: body.mode ?? FunnelMode.UNIQUE_USERS,
    dateRangeDays: body.dateRangeDays ?? 7,
    filters: body.filters || [],
    funnelType: body.funnelType,
    journeyType: body.journeyType,
    stepOrderType: body.stepOrderType,
    windowSeconds: body.windowSeconds,
    steps: body.steps,
    anchorEvent: body.anchorEvent,
    direction: body.direction,
    depth: body.depth,
  };
  MOCK_FUNNELS_JOURNEYS_ALL.unshift(newItem);
  // Return the detail shape so the create flow can navigate straight to the detail
  // page without a follow-up GET.
  const data =
    kind === "FUNNEL"
      ? projectFunnelDetail(newItem)
      : projectJourneyDetail(newItem);
  return { data, status: 201 };
}

function mockPutFunnelOrJourney(
  id: string,
  request: MockRequest,
  expectedKind: "FUNNEL" | "JOURNEY",
): MockResponse {
  let body: any = {};
  try {
    body = JSON.parse(request.body || "{}");
  } catch {
    /* ignore */
  }

  const index = MOCK_FUNNELS_JOURNEYS_ALL.findIndex((item) => item.id === id);
  if (index === -1) {
    return { data: null, status: 404 };
  }
  if (MOCK_FUNNELS_JOURNEYS_ALL[index].kind !== expectedKind) {
    return { data: null, status: 404 };
  }
  // PUT bodies (UpdateFunnelRequestBody / CreateJourneyRequestBody) both use
  // `expiry`. Accept legacy `expiryDate` too for back-compat with older callers.
  const { expiryDate: legacyExpiry, ...bodyRest } = body;
  const merged: MockFunnelJourneyRow = {
    ...MOCK_FUNNELS_JOURNEYS_ALL[index],
    ...bodyRest,
    status: "IN_PROGRESS",
    updatedAt: new Date().toISOString(),
  };
  if (bodyRest.expiry !== undefined) {
    merged.expiry = bodyRest.expiry;
  } else if (legacyExpiry !== undefined) {
    merged.expiry = legacyExpiry;
  }
  MOCK_FUNNELS_JOURNEYS_ALL[index] = merged;
  // Return the detail shape so the update flow can repaint the detail page
  // immediately on success.
  const data =
    expectedKind === "FUNNEL"
      ? projectFunnelDetail(merged)
      : projectJourneyDetail(merged);
  return { data, status: 200 };
}

export function handleFunnelEndpoints(
  pathname: string,
  method: string,
  request: MockRequest,
): MockResponse {
  const pathOnly = pathname.split("?")[0].replace(/\/$/, "");

  /** Collection: /v1/funnel or /v1/funnels (journeys: optional s). */
  const funnelCollectionSuffix = /\/v1\/funnels?$/;
  const journeyCollectionSuffix = /\/v1\/journeys?$/;
  const funnelIdPath = /\/v1\/funnels?\/([^/]+)$/;
  const journeyIdPath = /\/v1\/journeys?\/([^/]+)$/;

  if (method === "POST" && funnelCollectionSuffix.test(pathOnly)) {
    return mockPostCreateFunnelOrJourney(request, "FUNNEL");
  }
  if (method === "POST" && journeyCollectionSuffix.test(pathOnly)) {
    return mockPostCreateFunnelOrJourney(request, "JOURNEY");
  }

  // POST /v1/funnels/:id/stop and /v1/journeys/:id/stop — stop auto-refresh.
  // Flips the row's funnel_type/journey_type from AUTO → ONCE so it reads as
  // COMPLETED and is excluded from any (mock) cron logic.
  const stopFunnelMatch = pathOnly.match(/\/v1\/funnels?\/([^/]+)\/stop$/);
  if (method === "POST" && stopFunnelMatch) {
    return mockStopAuto(stopFunnelMatch[1], "FUNNEL");
  }
  const stopJourneyMatch = pathOnly.match(/\/v1\/journeys?\/([^/]+)\/stop$/);
  if (method === "POST" && stopJourneyMatch) {
    return mockStopAuto(stopJourneyMatch[1], "JOURNEY");
  }

  // DELETE /v1/funnels/:id and /v1/journeys/:id — cascading delete on the mock store.
  if (method === "DELETE") {
    const funnelDel = pathOnly.match(/\/v1\/funnels?\/([^/]+)$/);
    if (funnelDel) {
      return mockCascadeDelete(funnelDel[1], "FUNNEL");
    }
    const journeyDel = pathOnly.match(/\/v1\/journeys?\/([^/]+)$/);
    if (journeyDel) {
      return mockCascadeDelete(journeyDel[1], "JOURNEY");
    }
  }

  if (method === "PUT") {
    const funnelPut = pathOnly.match(funnelIdPath);
    if (funnelPut) {
      return mockPutFunnelOrJourney(funnelPut[1], request, "FUNNEL");
    }
    const journeyPut = pathOnly.match(journeyIdPath);
    if (journeyPut) {
      return mockPutFunnelOrJourney(journeyPut[1], request, "JOURNEY");
    }
  }

  // ── Named GET endpoints — must be checked before the generic /{id} matcher ──

  if (method === "GET" && pathOnly.endsWith("/v1/funnels/events")) {
    return { data: { events: MOCK_FUNNEL_EVENTS }, status: 200 };
  }

  // GET /v1/funnels/filters/{filterKey}/values (check before /filters to avoid prefix match)
  const filterValuesMatch = pathOnly.match(
    /\/v1\/funnels\/filters\/([^/]+)\/values$/,
  );
  if (method === "GET" && filterValuesMatch) {
    const filterKey = decodeURIComponent(filterValuesMatch[1]);
    return {
      data: { values: MOCK_FUNNEL_FILTER_VALUES[filterKey] ?? [] },
      status: 200,
    };
  }

  // GET /v1/funnels/filters — returns only the list of filter key strings
  if (method === "GET" && pathOnly.endsWith("/v1/funnels/filters")) {
    return { data: { filters: MOCK_FUNNEL_FILTER_KEYS }, status: 200 };
  }

  if (method === "GET" && pathname.includes("/v1/tags")) {
    return { data: { tags: MOCK_TAGS }, status: 200 };
  }

  if (method === "GET") {
    const funnelDetail = pathOnly.match(funnelIdPath);
    if (funnelDetail) {
      return mockSavedResourceDetail(funnelDetail[1], "FUNNEL");
    }
    const journeyDetail = pathOnly.match(journeyIdPath);
    if (journeyDetail) {
      return mockSavedResourceDetail(journeyDetail[1], "JOURNEY");
    }
    if (funnelCollectionSuffix.test(pathOnly)) {
      return mockFunnelListing(request);
    }
    if (journeyCollectionSuffix.test(pathOnly)) {
      return mockJourneyListing(request);
    }
  }

  if (
    pathname.includes(API_ROUTES.FUNNEL_CREATE.apiPath) &&
    method === API_ROUTES.FUNNEL_CREATE.method
  ) {
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }
    const { analyze } = getMockFunnelAnalyzeAndTrendFromBody(body);
    return { data: analyze, status: 200 };
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
    const { trend } = getMockFunnelAnalyzeAndTrendFromBody(body);
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

  if (pathname.includes("/v1/journey/explore") && method === "POST") {
    let body: any = {};
    try {
      body = JSON.parse(request.body || "{}");
    } catch {
      /* ignore */
    }
    const direction = body.direction || "START";
    const anchorEvent = body.anchorEvent || "";

    // Check if this is for the onboarding journey
    if (anchorEvent === "App_Launch" && direction === "START") {
      return { data: MOCK_ONBOARDING_JOURNEY_RESPONSE, status: 200 };
    }

    const data =
      direction === "reverse" ? MOCK_JOURNEY_REVERSE : MOCK_JOURNEY_FORWARD;
    return { data, status: 200 };
  }

  return { data: { message: "Unknown funnel endpoint" }, status: 404 };
}
