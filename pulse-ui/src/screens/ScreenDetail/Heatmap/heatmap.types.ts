/**
 * Heatmap API types — wire (server) vs normalized (UI).
 * v1 heatmap fetches are read-only; use TanStack Query useQuery (not useMutation).
 */

export interface HeatmapTimeRange {
  start: string;
  end: string;
}

/**
 * Sent as query/body `breakpoint`; must match `interaction_heatmaps_daily.Breakpoint`
 * (see `backend/db/prod/clickhouse/otel.interaction_heatmaps_daily.sql` MV).
 */
export const HEATMAP_BREAKPOINT_VALUES = [
  "Mobile_Small",
  "Mobile_Medium",
  "Tablet_Large",
  "Web_Extra_Large",
] as const;

export type HeatmapBreakpoint = (typeof HEATMAP_BREAKPOINT_VALUES)[number];

/**
 * UI label for each wire `breakpoint` value (same string with underscores as spaces).
 * {@link heatmapFiltersToRequestArgs} still sends the underscore form to the API.
 */
export function heatmapBreakpointDisplayLabel(wireValue: string): string {
  return wireValue.trim().replace(/_/g, " ");
}

export const HEATMAP_BREAKPOINT_NAMES: Record<HeatmapBreakpoint, string> =
  Object.fromEntries(
    HEATMAP_BREAKPOINT_VALUES.map((v) => [v, heatmapBreakpointDisplayLabel(v)]),
  ) as Record<HeatmapBreakpoint, string>;

/** Legacy UI values pre–MV rename — normalized on API request. */
export const LEGACY_HEATMAP_BREAKPOINT_TO_API: Record<
  string,
  HeatmapBreakpoint
> = {
  small_mobile: "Mobile_Small",
  medium_folding: "Mobile_Medium",
  medium_mobile: "Mobile_Medium",
  medium_mobile_wide: "Mobile_Medium",
  large_tablet: "Tablet_Large",
  extra_large_web: "Web_Extra_Large",
};

/** Query params for GET /v1/heatmap/data */
export interface HeatmapDataQueryParams {
  screenName: string;
  from?: string;
  to?: string;
  app_version?: string;
  platform?: string;
  /** Region filter — wire name `region` (matches Java). */
  region?: string;
  breakpoint?: HeatmapBreakpoint | string;
}

/** Body for POST .../heatmap/data when filters are heavy */
export interface HeatmapDataRequestBody {
  screenName: string;
  timeRange: HeatmapTimeRange;
  app_version?: string;
  platform?: string;
  region?: string;
  breakpoint?: HeatmapBreakpoint | string;
}

export interface HeatmapMetadata {
  screenName: string;
  ui_hash: string;
  screenshot_url: string;
  /**
   * Optional rotating backgrounds for the same heatmap (normalized glow_map applies to all).
   * When present and non-empty, UI carousel uses this order; `screenshot_url` is ignored for the list
   * but may still duplicate index 0 for legacy clients.
   */
  screenshot_urls?: string[];
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

/** One Pulse interaction score tied to a screen element (Key actions / interaction map). */
export interface HeatmapPulseInteractionScore {
  /** Backend interaction / critical-interaction id when available */
  interaction_id?: string;
  name?: string;
  score: number;
}

/**
 * Axis-aligned element bounds on the screenshot, 0–1 normalized (top-left origin) or pixel coords (see normalizer).
 * `interaction_scores` lists Pulse interactions that involve this element; optional `avg_score` may be server-provided.
 */
export interface HeatmapInteractionElementRegion {
  element_id?: string;
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
  interaction_scores: HeatmapPulseInteractionScore[];
  avg_score?: number;
}

export interface HeatmapInteractionMapLayer {
  regions: HeatmapInteractionElementRegion[];
}

/**
 * Row for right-rail Pulse interactions list (wire + normalized).
 * `avg_score` is 0–1 when present; `null` when the backend has no score yet.
 */
export interface HeatmapInteractionsMetadataRow {
  interaction_name: string;
  avg_score: number | null;
}

/** Raw `layers` object from GET/POST before normalization (spatial layers only). */
export interface HeatmapDataWireLayers {
  glow_map: HeatmapGlowPoint[];
  frustration_map: {
    rage_taps: HeatmapFrustrationPoint[];
    dead_taps: HeatmapFrustrationPoint[];
  };
  observability_map: {
    error_clicks: HeatmapErrorClickPoint[];
    latency_hotspots: HeatmapLatencyHotspot[];
  };
  /** Overlay rectangles + per-element Pulse scores; optional on wire. */
  interaction_map?: HeatmapInteractionMapLayer;
  /** Below-the-fold metrics (clicks with scroll offset > 0). */
  below_fold_metrics?: {
    total_clicks: number;
    total_click_bins: number;
    rage_taps: number;
    rage_bins: number;
    dead_taps: number;
    dead_bins: number;
  };
}

/**
 * Parsed JSON body for heatmap data before `normalizeHeatmapWireResponse`.
 * `interactions_metadata` is a **top-level** sibling of `metadata` and `layers` on the wire
 * (not nested under `layers`). `interaction_map` remains under `layers`.
 */
export interface HeatmapDataWireResponse {
  metadata: HeatmapMetadata;
  layers: HeatmapDataWireLayers;
  interactions_metadata?: HeatmapInteractionsMetadataRow[];
}

/**
 * Normalized heatmap payload for the UI — mirrors the wire: **`interactions_metadata` is only
 * top-level** (sibling of `metadata` and `layers`). Spatial layers + `interaction_map` stay under `layers`.
 */
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
    /** Optional overlay (from wire `layers.interaction_map`). */
    interaction_map?: HeatmapInteractionMapLayer;
    /** Below-the-fold metrics for right-side table display. */
    below_fold_metrics?: {
      total_clicks: number;
      total_click_bins: number;
      rage_taps: number;
      rage_bins: number;
      dead_taps: number;
      dead_bins: number;
    };
  };
  /** Right-rail Pulse interaction table — not part of `layers`. */
  interactions_metadata?: HeatmapInteractionsMetadataRow[];
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
