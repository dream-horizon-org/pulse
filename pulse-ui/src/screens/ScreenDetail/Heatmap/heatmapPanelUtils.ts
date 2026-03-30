import type {
  HeatmapDataResponse,
  HeatmapGlowPoint,
} from "./heatmap.types";

export type HeatmapSignal = "tap" | "scroll" | "rage" | "dead" | "gesture";
export type HeatmapFocusLens = "all" | "key";

export const HEATMAP_SIGNALS: { id: HeatmapSignal; label: string }[] = [
  { id: "tap", label: "Tap" },
  { id: "scroll", label: "Scroll" },
  { id: "rage", label: "Rage" },
  { id: "dead", label: "Dead" },
  { id: "gesture", label: "Gesture" },
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
