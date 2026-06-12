/**
 * Heatmap breakpoint labels — must stay aligned with
 * `otel.interaction_heatmaps_daily_mv` in `backend/db/prod/clickhouse/otel.interaction_heatmaps_daily.sql`
 * (multiIf order and string values).
 */
export const HeatmapBreakpoint = {
  Web_Extra_Large: "Web_Extra_Large",
  Mobile_Medium: "Mobile_Medium",
  Tablet_Large: "Tablet_Large",
  Mobile_Small: "Mobile_Small",
} as const;

export type HeatmapBreakpointLabel =
  (typeof HeatmapBreakpoint)[keyof typeof HeatmapBreakpoint];

/** `Platform` / `snapshot_source` value for web sessions (ClickHouse, session replay). */
export const HEATMAP_PLATFORM_WEB = "Web";

function assertPositiveViewport(width: number, height: number): void {
  if (!Number.isFinite(width) || !Number.isFinite(height)) {
    throw new RangeError("viewport width and height must be finite numbers");
  }
  if (width <= 0 || height <= 0) {
    throw new RangeError("viewport width and height must be positive");
  }
}

/**
 * Maps platform + viewport size to the same breakpoint bucket as ClickHouse heatmap MV.
 *
 * Order (must match ClickHouse `multiIf`):
 * 1. Web + width > 1024 → Web_Extra_Large
 * 2. Web → Mobile_Medium
 * 3. width > 600 → Tablet_Large
 * 4. width <= 600 + height/width <= 1.5 → Mobile_Small
 * 5. else → Mobile_Medium
 *
 * Web viewport is px; native (Android, iOS, …) is dp — same numeric thresholds as MV.
 */
export function resolveHeatmapBreakpoint(
  platform: string,
  viewportWidth: number,
  viewportHeight: number,
): HeatmapBreakpointLabel {
  assertPositiveViewport(viewportWidth, viewportHeight);

  if (platform === HEATMAP_PLATFORM_WEB && viewportWidth > 1024) {
    return HeatmapBreakpoint.Web_Extra_Large;
  }
  if (platform === HEATMAP_PLATFORM_WEB) {
    return HeatmapBreakpoint.Mobile_Medium;
  }
  if (viewportWidth > 600) {
    return HeatmapBreakpoint.Tablet_Large;
  }

  const aspectHeightOverWidth = viewportHeight / viewportWidth;
  if (viewportWidth <= 600 && aspectHeightOverWidth <= 1.5) {
    return HeatmapBreakpoint.Mobile_Small;
  }
  return HeatmapBreakpoint.Mobile_Medium;
}
