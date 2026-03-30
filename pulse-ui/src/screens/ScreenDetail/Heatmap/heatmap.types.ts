/**
 * Heatmap API types — aligned with wireframes/heatmap/HEATMAP_UI.md
 * v1 heatmap fetches are read-only; use TanStack Query useQuery (not useMutation).
 */

export interface HeatmapTimeRange {
  start: string;
  end: string;
}

export type HeatmapIncludeLayer = "glow" | "frustration" | "observability";

/** Query param on Screen → Heatmap URL and GET /v1/heatmap/data (mock only). */
export const RCA_HEATMAP_SIGNAL_QUERY_PARAM = "rcaHeatmapSignal";

/**
 * When opening heatmap from Interaction → Root Cause, holds encoded `pathname` + `search`
 * so Screen detail “Back” can return to that view (e.g. `?tab=root-cause`).
 */
export const HEATMAP_RETURN_TO_QUERY_PARAM = "heatmapReturnTo";

/** Decode {@link HEATMAP_RETURN_TO_QUERY_PARAM} — same-origin `/projects/...` paths only. */
export function parseSafeHeatmapReturnTo(encoded: string | null): string | null {
  if (encoded == null || encoded.trim() === "") return null;
  let decoded: string;
  try {
    decoded = decodeURIComponent(encoded.trim());
  } catch {
    return null;
  }
  decoded = decoded.trim();
  if (!decoded.startsWith("/")) return null;
  if (decoded.startsWith("//")) return null;
  if (decoded.includes("://")) return null;
  if (!decoded.startsWith("/projects/")) return null;
  const noHash = decoded.split("#")[0] ?? "";
  if (noHash.length > 4096) return null;
  return noHash;
}

/** Query params for GET /v1/heatmap/data */
export interface HeatmapDataQueryParams {
  screenName: string;
  from?: string;
  to?: string;
  app_version?: string;
  platform?: string;
  aspect_ratio?: string;
  cohort_id?: string;
  /** Comma-separated layers */
  layers?: string;
  /** Mock: `poor` | `average` | `good` — from RCA `heatmap_signal_quality`. */
  rcaHeatmapSignal?: string;
}

/** Body for POST .../heatmap/data when filters are heavy */
export interface HeatmapDataRequestBody {
  screenName: string;
  timeRange: HeatmapTimeRange;
  app_version?: string;
  platform?: string;
  aspect_ratio?: string;
  cohort_id?: string;
  includeLayers?: HeatmapIncludeLayer[];
  /** Mock: aligns with RCA narrative when using POST. */
  rcaHeatmapSignal?: string;
}

export interface HeatmapMetadata {
  screenName: string;
  ui_hash: string;
  screenshot_url: string;
  total_events: number;
  app_version?: string;
  platform?: string;
  aspect_ratio?: string;
  created_at?: string;
}

export interface HeatmapGlowPoint {
  x: number;
  y: number;
  weight: number;
}

export interface HeatmapFrustrationPoint {
  x: number;
  y: number;
  weight: number;
  avg_sequence_count?: number;
}

export interface HeatmapErrorClickPoint {
  x: number;
  y: number;
  weight: number;
  error_code?: string | number;
}

export interface HeatmapLatencyHotspot {
  x: number;
  y: number;
  avg_latency_ms: number;
  weight?: number;
}

export interface HeatmapDataResponse {
  metadata: HeatmapMetadata;
  layers: {
    glow_map: HeatmapGlowPoint[];
    frustration_map: {
      rage: HeatmapFrustrationPoint[];
      dead: HeatmapFrustrationPoint[];
    };
    observability_map: {
      error_clicks: HeatmapErrorClickPoint[];
      latency_hotspots: HeatmapLatencyHotspot[];
    };
  };
}

export interface HeatmapCompareTarget {
  screenName: string;
  variantId?: string | null;
}

export interface HeatmapCompareRequestBody extends HeatmapDataRequestBody {
  compare: HeatmapCompareTarget;
  variantId?: string | null;
}

export interface HeatmapCompareResponse {
  shared: {
    timeRange: HeatmapTimeRange;
    filtersApplied: Record<string, unknown>;
  };
  left: HeatmapDataResponse;
  right: HeatmapDataResponse;
  colorScaleMax?: number;
}
