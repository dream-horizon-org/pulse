/**
 * Heatmap API types — aligned with wireframes/heatmap/HEATMAP_UI.md
 * v1 heatmap fetches are read-only; use TanStack Query useQuery (not useMutation).
 */

export interface HeatmapTimeRange {
  start: string;
  end: string;
}

export type HeatmapIncludeLayer = "glow" | "frustration" | "observability";

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
    /** Key-actions view: rectangular regions + per–Pulse-interaction scores (same heatmap API). */
    interaction_map?: HeatmapInteractionMapLayer;
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
