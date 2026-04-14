import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

/** Must match RCA 7-day window: 7 calendar days ending on `date` (UTC). */
export const LOOKBACK_DAYS = 7;

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * `[start, end)` ISO instants for error-attribution / RCA alignment.
 * `end` defaults to current instant; pass `asOf` when the page exposes it.
 */
export function getErrorAttributionWindowIso(
  date: string | null | undefined,
  asOfIso?: string | null,
): { start: string; end: string } {
  const validDate =
    !!date && date !== "Invalid Date" && DATE_RE.test(String(date));
  const anchor = validDate ? String(date) : dayjs.utc().format("YYYY-MM-DD");
  const start = dayjs
    .utc(anchor)
    .subtract(LOOKBACK_DAYS - 1, "day")
    .startOf("day")
    .toISOString();
  const end =
    asOfIso != null && String(asOfIso).trim() !== ""
      ? String(asOfIso)
      : new Date().toISOString();
  return { start, end };
}
