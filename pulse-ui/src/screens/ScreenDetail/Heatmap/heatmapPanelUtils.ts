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
 * True when Key actions lens should be offered: overlay (`payload.layers.interaction_map`) and/or
 * top-level `payload.interactions_metadata`.
 */
export function heatmapShowsKeyActionsLens(
  payload: HeatmapDataResponse | null | undefined,
): boolean {
  if (payload == null || typeof payload !== "object") return false;
  const layers = payload.layers;
  if (layers != null && typeof layers === "object") {
    if (
      Object.prototype.hasOwnProperty.call(layers, "interaction_map") &&
      layers.interaction_map != null
    ) {
      return true;
    }
  }
  const meta = payload.interactions_metadata;
  return Array.isArray(meta) && meta.length > 0;
}

export function glowLayerForSignal(
  data: HeatmapDataResponse | null | undefined,
  signal: HeatmapSignal,
): HeatmapGlowPoint[] {
  if (!data) return [];
  const base = data.layers.glow_map ?? [];
  if (signal === "rage") {
    return (
      data.layers.frustration_map?.rage?.map((r) => ({
        x: r.x,
        y: r.y,
        weight: r.weight,
      })) ?? base
    );
  }
  if (signal === "dead") {
    return (
      data.layers.frustration_map?.dead?.map((r) => ({
        x: r.x,
        y: r.y,
        weight: r.weight,
      })) ?? base
    );
  }
  return base;
}

export function compareSharedWeightMax(
  left: HeatmapDataResponse | null | undefined,
  right: HeatmapDataResponse | null | undefined,
  signal: HeatmapSignal,
): number {
  const a = glowLayerForSignal(left, signal);
  const b = glowLayerForSignal(right, signal);
  const ma = a.reduce((m, p) => Math.max(m, p.weight), 0);
  const mb = b.reduce((m, p) => Math.max(m, p.weight), 0);
  return Math.max(ma, mb, 1);
}
