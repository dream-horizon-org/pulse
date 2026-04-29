import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import type { RcaHeatmapFiltersWireV1 } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";

dayjs.extend(utc);

/** Default lookback days — must match server `rootcause-default.conf` default. */
export const DEFAULT_ROOT_CAUSE_LOOKBACK_DAYS = 7;

/**
 * Mirrors {@code RootCauseQueryBuilder.Window}: anchor calendar day, lookback, exclusive end.
 * Used for heatmap evidence and AI narrative when the API is called with `date` + `asOf` only.
 */
export function rcaWindowFromAnchorAndAsOf(
  anchorYyyyMmDd: string,
  endExclusiveIso: string,
  lookbackDays: number = DEFAULT_ROOT_CAUSE_LOOKBACK_DAYS,
): { windowStartIso: string; windowEndIso: string } {
  const anchor = dayjs.utc(anchorYyyyMmDd, "YYYY-MM-DD", true);
  if (!anchor.isValid() || !/^\d{4}-\d{2}-\d{2}$/.test(anchorYyyyMmDd)) {
    return { windowStartIso: "", windowEndIso: endExclusiveIso };
  }
  const startInclusive = anchor
    .subtract(lookbackDays - 1, "day")
    .startOf("day");
  return {
    windowStartIso: startInclusive.toISOString(),
    windowEndIso: endExclusiveIso,
  };
}

/**
 * Maps segment dimension keys to heatmap filter fields — must stay aligned with
 * {@code RcaRelatedHeatmapsMerger.buildHeatmapFilters} (Java).
 */
function dimensionField(
  dimensions: Record<string, string> | null | undefined,
  dimKey: string,
): string | null {
  if (dimensions == null) {
    return null;
  }
  const v = dimensions[dimKey];
  if (v == null || String(v).trim() === "") {
    return null;
  }
  return String(v);
}

/**
 * UTC calendar date of the inclusive window start (`LocalDate.ofInstant(startInclusive, UTC)`).
 */
export function fromDateFromStartInclusiveUtc(
  startInclusiveIso: string,
): string {
  const t = dayjs.utc(startInclusiveIso);
  if (!t.isValid()) {
    return "";
  }
  return t.format("YYYY-MM-DD");
}

/**
 * UTC calendar date of the last instant inside the window: `endExclusive - 1 ns`
 * (`LocalDate.ofInstant(window.endExclusive.minusNanos(1), UTC)`).
 * JavaScript uses millisecond resolution; `endMs - 1` matches Java for typical ISO instants.
 */
export function toDateFromExclusiveEndUtc(endExclusiveIso: string): string {
  const endMs = Date.parse(endExclusiveIso);
  if (Number.isNaN(endMs)) {
    return "";
  }
  const lastInstantInside = endMs - 1;
  return dayjs.utc(lastInstantInside).format("YYYY-MM-DD");
}

/**
 * Heatmap filters for screen RCA evidence cards, aligned with
 * {@code RcaRelatedHeatmapsMerger} (Platform / AppVersion / GeoState, from_date / to_date).
 */
export function buildScreenRcaHeatmapFilters(
  dimensions: Record<string, string> | null | undefined,
  windowStartIso: string,
  windowEndIso: string,
): RcaHeatmapFiltersWireV1 {
  const fromIso = fromDateFromStartInclusiveUtc(windowStartIso);
  const toIso = toDateFromExclusiveEndUtc(windowEndIso);
  return {
    breakpoint: null,
    platform: dimensionField(dimensions, "Platform"),
    app_version: dimensionField(dimensions, "AppVersion"),
    geographical_region: dimensionField(dimensions, "GeoState"),
    from_date: fromIso === "" ? null : fromIso,
    to_date: toIso === "" ? null : toIso,
  };
}
