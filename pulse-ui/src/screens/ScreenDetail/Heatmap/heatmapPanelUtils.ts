import type {
  HeatmapDataResponse,
  HeatmapGlowPoint,
} from "./heatmap.types";

export type HeatmapSignal = "tap" | "rage" | "dead";
export type HeatmapFocusLens = "all" | "key";

export const HEATMAP_SIGNALS: { id: HeatmapSignal; label: string }[] = [
  { id: "tap", label: "Tap" },
  { id: "rage", label: "Rage" },
  { id: "dead", label: "Dead" },
];

export function formatAvgTime(seconds: number | null | undefined): string {
  if (seconds == null || Number.isNaN(seconds)) return "N/A";
  if (seconds >= 1) return `${seconds.toFixed(1)}s`;
  return `${Math.round(seconds * 1000)}ms`;
}

export function formatInt(n: number): string {
  if (n == null || Number.isNaN(n)) return "—";
  return n.toLocaleString();
}

/**
 * True when the Interaction map lens may be offered — only when the wire includes
 * `layers.interaction_map` (spatial overlay). Top-level `interactions_metadata` is unrelated.
 */
export function heatmapShowsKeyActionsLens(
  payload: HeatmapDataResponse | null | undefined,
): boolean {
  if (payload == null || typeof payload !== "object") return false;
  const layers = payload.layers;
  if (layers == null || typeof layers !== "object") return false;
  if (
    !Object.prototype.hasOwnProperty.call(layers, "interaction_map") ||
    layers.interaction_map == null
  ) {
    return false;
  }
  const regions = layers.interaction_map.regions;
  return Array.isArray(regions) && regions.length > 0;
}

/**
 * Density layer for the selected signal. Tap uses `glow_map`; rage/dead use only
 * `frustration_map` — missing or empty arrays yield an empty layer (no fallback to tap).
 */
export function glowLayerForSignal(
  data: HeatmapDataResponse | null | undefined,
  signal: HeatmapSignal,
): HeatmapGlowPoint[] {
  if (!data) return [];
  const base = data.layers.glow_map ?? [];
  if (signal === "rage") {
    const rage = data.layers.frustration_map?.rage ?? [];
    return rage.map((r) => ({ x: r.x, y: r.y, weight: r.weight }));
  }
  if (signal === "dead") {
    const dead = data.layers.frustration_map?.dead ?? [];
    return dead.map((r) => ({ x: r.x, y: r.y, weight: r.weight }));
  }
  return base;
}

/** Max `weight` in a glow layer — heatmap.js scaling uses this like compare-mode `sharedWeightMax`. */
export function glowLayerWeightMax(points: HeatmapGlowPoint[]): number {
  const m = points.reduce((acc, p) => Math.max(acc, p.weight), 0);
  return Math.max(m, 1);
}

export function compareSharedWeightMax(
  left: HeatmapDataResponse | null | undefined,
  right: HeatmapDataResponse | null | undefined,
  signal: HeatmapSignal,
): number {
  const a = glowLayerForSignal(left, signal);
  const b = glowLayerForSignal(right, signal);
  return Math.max(glowLayerWeightMax(a), glowLayerWeightMax(b));
}
