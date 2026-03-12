export interface MockFunnelStep {
  id: string;
  eventName: string;
  completed: number;
  conversionRate: number;
  dropoffRate: number;
  dropoffCount: number;
  medianTimeToStep: number | null;
}

export interface MockFunnelData {
  totalConversionRate: number;
  conversionTrend: number;
  steps: MockFunnelStep[];
}

export interface MockFunnelGroupedRow {
  groupValue: string;
  steps: MockFunnelStep[];
}

export interface MockJourneyNode {
  id: string;
  name: string;
  userCount: number;
  userPercent: number;
  isExit?: boolean;
  children?: MockJourneyNode[];
}

export interface MockJourneyLink {
  source: string;
  target: string;
  value: number;
}

export interface MockJourneyData {
  nodes: { name: string }[];
  links: MockJourneyLink[];
}

export const AVAILABLE_EVENTS = [
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

export const FILTER_OPTIONS: Record<string, string[]> = {
  "App Version": ["4.2.1", "4.2.0", "4.1.9", "4.1.8", "4.1.7"],
  "Device Model": [
    "iPhone 15 Pro",
    "iPhone 14",
    "iPhone 13",
    "Samsung Galaxy S24",
    "Samsung Galaxy S23",
    "Pixel 8",
    "Pixel 7",
    "OnePlus 12",
  ],
  OS: ["iOS", "Android"],
  Country: [
    "United States",
    "India",
    "United Kingdom",
    "Germany",
    "Japan",
    "Brazil",
    "Canada",
    "Australia",
  ],
  City: [
    "San Francisco",
    "New York",
    "London",
    "Mumbai",
    "Berlin",
    "Tokyo",
    "São Paulo",
    "Toronto",
    "Sydney",
  ],
};

export const MOCK_FUNNEL_DATA: MockFunnelData = {
  totalConversionRate: 32.4,
  conversionTrend: 2.1,
  steps: [
    {
      id: "step-1",
      eventName: "Screen_View: Home",
      completed: 14200,
      conversionRate: 100,
      dropoffRate: 0,
      dropoffCount: 0,
      medianTimeToStep: null,
    },
    {
      id: "step-2",
      eventName: "Screen_View: Product Detail",
      completed: 9680,
      conversionRate: 68.2,
      dropoffRate: 31.8,
      dropoffCount: 4520,
      medianTimeToStep: 4.2,
    },
    {
      id: "step-3",
      eventName: "Tap: Add to Cart",
      completed: 6840,
      conversionRate: 48.2,
      dropoffRate: 29.3,
      dropoffCount: 2840,
      medianTimeToStep: 12.8,
    },
    {
      id: "step-4",
      eventName: "Tap: Checkout",
      completed: 5100,
      conversionRate: 35.9,
      dropoffRate: 25.4,
      dropoffCount: 1740,
      medianTimeToStep: 8.6,
    },
    {
      id: "step-5",
      eventName: "Tap: Place Order",
      completed: 4600,
      conversionRate: 32.4,
      dropoffRate: 9.8,
      dropoffCount: 500,
      medianTimeToStep: 45.3,
    },
  ],
};

export const MOCK_GROUPED_DATA: Record<string, MockFunnelGroupedRow[]> = {
  OS: [
    {
      groupValue: "iOS",
      steps: [
        {
          id: "s1",
          eventName: "Screen_View: Home",
          completed: 8200,
          conversionRate: 100,
          dropoffRate: 0,
          dropoffCount: 0,
          medianTimeToStep: null,
        },
        {
          id: "s2",
          eventName: "Screen_View: Product Detail",
          completed: 5900,
          conversionRate: 72.0,
          dropoffRate: 28.0,
          dropoffCount: 2300,
          medianTimeToStep: 3.8,
        },
        {
          id: "s3",
          eventName: "Tap: Add to Cart",
          completed: 4300,
          conversionRate: 52.4,
          dropoffRate: 27.1,
          dropoffCount: 1600,
          medianTimeToStep: 11.2,
        },
        {
          id: "s4",
          eventName: "Tap: Checkout",
          completed: 3300,
          conversionRate: 40.2,
          dropoffRate: 23.3,
          dropoffCount: 1000,
          medianTimeToStep: 7.9,
        },
        {
          id: "s5",
          eventName: "Tap: Place Order",
          completed: 3000,
          conversionRate: 36.6,
          dropoffRate: 9.1,
          dropoffCount: 300,
          medianTimeToStep: 42.1,
        },
      ],
    },
    {
      groupValue: "Android",
      steps: [
        {
          id: "s1",
          eventName: "Screen_View: Home",
          completed: 6000,
          conversionRate: 100,
          dropoffRate: 0,
          dropoffCount: 0,
          medianTimeToStep: null,
        },
        {
          id: "s2",
          eventName: "Screen_View: Product Detail",
          completed: 3780,
          conversionRate: 63.0,
          dropoffRate: 37.0,
          dropoffCount: 2220,
          medianTimeToStep: 4.8,
        },
        {
          id: "s3",
          eventName: "Tap: Add to Cart",
          completed: 2540,
          conversionRate: 42.3,
          dropoffRate: 32.8,
          dropoffCount: 1240,
          medianTimeToStep: 14.9,
        },
        {
          id: "s4",
          eventName: "Tap: Checkout",
          completed: 1800,
          conversionRate: 30.0,
          dropoffRate: 29.1,
          dropoffCount: 740,
          medianTimeToStep: 9.6,
        },
        {
          id: "s5",
          eventName: "Tap: Place Order",
          completed: 1600,
          conversionRate: 26.7,
          dropoffRate: 11.1,
          dropoffCount: 200,
          medianTimeToStep: 50.8,
        },
      ],
    },
  ],
};

export const MOCK_JOURNEY_FORWARD: MockJourneyData = {
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

export const MOCK_JOURNEY_REVERSE: MockJourneyData = {
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

export const CONVERSION_WINDOW_OPTIONS = [
  { value: "300", label: "5 Minutes" },
  { value: "900", label: "15 Minutes" },
  { value: "1800", label: "30 Minutes" },
  { value: "3600", label: "1 Hour" },
  { value: "14400", label: "4 Hours" },
  { value: "86400", label: "24 Hours" },
  { value: "259200", label: "3 Days" },
  { value: "604800", label: "7 Days" },
];

export const DATE_RANGE_OPTIONS = [
  { value: "today", label: "Today" },
  { value: "yesterday", label: "Yesterday" },
  { value: "7d", label: "Last 7 Days" },
  { value: "14d", label: "Last 14 Days" },
  { value: "30d", label: "Last 30 Days" },
  { value: "custom", label: "Custom Range" },
];

export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds.toFixed(1)}s`;
  if (seconds < 3600) return `${(seconds / 60).toFixed(1)}m`;
  return `${(seconds / 3600).toFixed(1)}h`;
}
