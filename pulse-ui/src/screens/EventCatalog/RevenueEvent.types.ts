export type RevenueEventConfig = {
  id: string;
  eventName: string;
  valueAttribute: string;
  /** Fixed currency code when not using currencyAttribute. */
  currency: string;
  /** LogAttributes key for per-event currency code. */
  currencyAttribute?: string;
  conversionWindowHours: number;
  configuredAt: string;
};

export const REVENUE_EVENT_CURRENCY_OPTIONS = [
  { value: "INR", label: "INR — Indian Rupee" },
  { value: "USD", label: "USD — US Dollar" },
  { value: "EUR", label: "EUR — Euro" },
  { value: "GBP", label: "GBP — British Pound" },
] as const;

export const SUPPORTED_CURRENCY_CODES = new Set<string>(
  REVENUE_EVENT_CURRENCY_OPTIONS.map((o) => o.value),
);

export const DEFAULT_CONVERSION_WINDOW_HOURS = 24;
export const DEFAULT_REVENUE_EVENT_PREVIEW_DAYS = 7;
export const MAX_REVENUE_EVENT_PREVIEW_DAYS = 30;

export const REVENUE_EVENT_PREVIEW_DAYS_OPTIONS = [
  { value: "7", label: "7 days" },
  { value: "15", label: "15 days" },
  { value: "30", label: "30 days" },
] as const;

export const NUMERIC_ATTRIBUTE_TYPES = new Set(["int", "double", "integer"]);
