import { EventAttribute } from "./EventCatalog.types";
import {
  REVENUE_EVENT_CURRENCY_OPTIONS,
  SUPPORTED_CURRENCY_CODES,
} from "./RevenueEvent.types";

const CURRENCY_ATTRIBUTE_PATTERN =
  /^(currency(_code)?|curr(ency)?|iso_currency|currencyCode)$/i;

export function detectCurrencyAttribute(
  attributes: EventAttribute[] | undefined,
): string | null {
  if (!attributes?.length) {
    return null;
  }
  const match = attributes.find((a) =>
    CURRENCY_ATTRIBUTE_PATTERN.test(a.attributeName.trim()),
  );
  return match?.attributeName ?? null;
}

export function normalizeCurrencyCode(value: string): string | null {
  const code = value.trim().toUpperCase();
  if (!code || !SUPPORTED_CURRENCY_CODES.has(code)) {
    return null;
  }
  return code;
}

export function pickDefaultCurrency(
  detected: string[],
  fallback = "INR",
): string {
  const supported = detected
    .map(normalizeCurrencyCode)
    .filter((c): c is string => c !== null);
  if (supported.length === 1) {
    return supported[0];
  }
  if (supported.includes(fallback)) {
    return fallback;
  }
  return fallback;
}

export function buildCurrencyAttributeOptions(
  attributes: EventAttribute[] | undefined,
) {
  if (!attributes?.length) {
    return [];
  }
  const stringAttrs = attributes.filter(
    (a) => (a.dataType?.toLowerCase() ?? "") === "string",
  );
  const source = stringAttrs.length > 0 ? stringAttrs : attributes;
  return [...source]
    .sort((a, b) => {
      const aMatch = CURRENCY_ATTRIBUTE_PATTERN.test(a.attributeName) ? 0 : 1;
      const bMatch = CURRENCY_ATTRIBUTE_PATTERN.test(b.attributeName) ? 0 : 1;
      if (aMatch !== bMatch) {
        return aMatch - bMatch;
      }
      return a.attributeName.localeCompare(b.attributeName);
    })
    .map((a) => ({
      value: a.attributeName,
      label: a.attributeName,
    }));
}

export function buildFixedCurrencyOptions() {
  return REVENUE_EVENT_CURRENCY_OPTIONS.map((o) => ({ ...o }));
}
