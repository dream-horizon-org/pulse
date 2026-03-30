import { normalizedGlowXY } from "./heatmapDisplay";
import type { HeatmapGlowPoint } from "./heatmap.types";

export function plumeSizePx(weight: number, maxWeight: number): number {
  if (maxWeight <= 0) return 48;
  return 48 + (weight / maxWeight) * 140;
}

const DOM_BUCKET_DIM = 24;
const DOM_RAW_POINT_SOFT_CAP = 384;

/**
 * One div per point is expensive; bucket into a grid and keep max weight per cell.
 */
export function bucketGlowForDom(points: HeatmapGlowPoint[]): HeatmapGlowPoint[] {
  if (points.length <= DOM_RAW_POINT_SOFT_CAP) return points;
  const dim = DOM_BUCKET_DIM;
  const map = new Map<string, HeatmapGlowPoint>();
  for (const p of points) {
    const gx = Math.min(dim - 1, Math.floor(p.x * dim));
    const gy = Math.min(dim - 1, Math.floor(p.y * dim));
    const key = `${gx},${gy}`;
    const x = (gx + 0.5) / dim;
    const y = (gy + 0.5) / dim;
    const prev = map.get(key);
    if (!prev) map.set(key, { x, y, weight: p.weight });
    else prev.weight = Math.max(prev.weight, p.weight);
  }
  return Array.from(map.values());
}

/** Bucketed points, strongest first, capped by `binBudget`. */
export function domGlowMapForBudget(
  glowMap: HeatmapGlowPoint[],
  binBudget: number,
): HeatmapGlowPoint[] {
  const norm = normalizedGlowXY(glowMap);
  const bucketed = bucketGlowForDom(norm);
  const sorted = [...bucketed].sort((a, b) => b.weight - a.weight);
  return sorted.slice(0, Math.min(binBudget, sorted.length));
}

/** All bucketed points after normalization (no hotspot slider). */
export function domGlowMapFull(glowMap: HeatmapGlowPoint[]): HeatmapGlowPoint[] {
  const norm = normalizedGlowXY(glowMap);
  return bucketGlowForDom(norm);
}

function lerpRgb(
  a: { r: number; g: number; b: number },
  b: { r: number; g: number; b: number },
  t: number,
): { r: number; g: number; b: number } {
  return {
    r: Math.round(a.r + (b.r - a.r) * t),
    g: Math.round(a.g + (b.g - a.g) * t),
    b: Math.round(a.b + (b.b - a.b) * t),
  };
}

export type HeatmapPlumePalette = "thermal" | "brand";

/** RGB along teal → amber → red (matches brand intensity legend). `t` in [0, 1]. */
export function heatRgbAtIntensity(t: number): { r: number; g: number; b: number } {
  const x = Math.min(1, Math.max(0, t));
  const teal = { r: 14, g: 201, b: 194 };
  const amber = { r: 252, g: 196, b: 25 };
  const red = { r: 250, g: 82, b: 82 };
  if (x <= 0.5) {
    return lerpRgb(teal, amber, x / 0.5);
  }
  return lerpRgb(amber, red, (x - 0.5) / 0.5);
}

/**
 * Classic thermal-style map: cold blue → cyan → green → yellow → orange → red.
 * Reads closer to common analytics heatmaps than brand teal.
 */
export function heatRgbThermalAtIntensity(t: number): { r: number; g: number; b: number } {
  const x = Math.min(1, Math.max(0, t));
  const stops: Array<{ t: number; r: number; g: number; b: number }> = [
    { t: 0, r: 45, g: 86, b: 210 },
    { t: 0.22, r: 55, g: 160, b: 230 },
    { t: 0.44, r: 55, g: 178, b: 115 },
    { t: 0.58, r: 238, g: 204, b: 65 },
    { t: 0.78, r: 247, g: 125, b: 55 },
    { t: 1, r: 205, g: 50, b: 52 },
  ];
  let i = 0;
  while (i < stops.length - 1 && x > stops[i + 1].t) i += 1;
  const a = stops[i];
  const b = stops[i + 1];
  const u = b.t === a.t ? 0 : (x - a.t) / (b.t - a.t);
  return {
    r: Math.round(a.r + (b.r - a.r) * u),
    g: Math.round(a.g + (b.g - a.g) * u),
    b: Math.round(a.b + (b.b - a.b) * u),
  };
}

function heatRgbForPalette(
  t: number,
  palette: HeatmapPlumePalette,
): { r: number; g: number; b: number } {
  return palette === "thermal" ? heatRgbThermalAtIntensity(t) : heatRgbAtIntensity(t);
}

/**
 * Radial fill for one bin: cooler colors for low relative weight, hotter for high
 * (aligned with the “Less / More activity” legend for the chosen palette).
 */
export function plumeRadialGradientForWeight(
  weight: number,
  maxWeight: number,
  palette: HeatmapPlumePalette = "thermal",
): string {
  const t = maxWeight > 0 ? Math.min(1, Math.max(0, weight / maxWeight)) : 0;
  const c = heatRgbForPalette(t, palette);
  const innerA = palette === "thermal" ? 0.72 : 0.78;
  const midA = palette === "thermal" ? 0.28 : 0.32;
  const inner = `rgba(${c.r},${c.g},${c.b},${innerA})`;
  const mid = `rgba(${c.r},${c.g},${c.b},${midA})`;
  return `radial-gradient(circle, ${inner} 0%, ${mid} 48%, transparent 76%)`;
}

/** Opacity scales with relative intensity so low bins stay subtle. */
export function plumeOpacityForWeight(
  weight: number,
  maxWeight: number,
  palette: HeatmapPlumePalette = "thermal",
): number {
  const t = maxWeight > 0 ? Math.min(1, Math.max(0, weight / maxWeight)) : 0;
  return palette === "thermal"
    ? 0.16 + t * 0.58
    : 0.18 + t * 0.62;
}
