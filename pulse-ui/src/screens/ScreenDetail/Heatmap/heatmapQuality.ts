import type { HeatmapDataResponse } from "./heatmap.types";

/**
 * Heatmap scores are **derived from API telemetry** (`glow_map` weights and
 * `total_events`), not from analyzing the screenshot image. The image is only
 * an underlay for aligning normalized x/y (0–1) to pixels.
 */

/** Inclusive lower bound for the “Good” band (70–100). */
export const HEATMAP_QUALITY_GOOD_MIN = 70;

/** Inclusive lower bound for the “Average” band (40–69). “Poor” is 0–39. */
export const HEATMAP_QUALITY_AVERAGE_MIN = 40;

/** Map a 0–100 score to Good / Average / Poor (same cutoffs as map quality). */
export function bandFromNumericScore(
  score: number,
): Exclude<HeatmapQualityMetrics["band"], "nodata"> {
  if (score >= HEATMAP_QUALITY_GOOD_MIN) return "good";
  if (score >= HEATMAP_QUALITY_AVERAGE_MIN) return "average";
  return "poor";
}

/** Text color aligned with grade chips (Good = teal, Average = orange, Poor = red). */
export function heatmapScoreColor(band: HeatmapQualityMetrics["band"]): string {
  switch (band) {
    case "good":
      return "#0ba09a";
    case "average":
      return "#c05621";
    case "poor":
      return "#c92a2a";
    default:
      return "var(--mantine-color-dimmed)";
  }
}

export function qualityLabelForBand(
  band: Exclude<HeatmapQualityMetrics["band"], "nodata">,
): string {
  if (band === "good") return "Good";
  if (band === "average") return "Average";
  return "Poor";
}

export interface HeatmapQualityMetrics {
  score: number | null;
  /** Good / Average / Poor / No data */
  label: string;
  /** Maps to UI legend chip styling */
  band: "good" | "average" | "poor" | "nodata";
  /**
   * Glow weight sum ÷ total events (capped at 100%). How much reported volume
   * is represented in heatmap bins.
   */
  eventWeightMatchPct: number | null;
  /**
   * Max bin weight ÷ sum of weights. How much of the map’s weight sits in the
   * single hottest bin (0–100%).
   */
  hotspotPeakPct: number | null;
}

export function getHeatmapQualityMetrics(
  payload: HeatmapDataResponse | null | undefined,
): HeatmapQualityMetrics {
  const glow = payload?.layers?.glow_map;
  if (!payload || !glow?.length) {
    return {
      score: null,
      label: "No data",
      band: "nodata",
      eventWeightMatchPct: null,
      hotspotPeakPct: null,
    };
  }

  const totalEvents = Math.max(1, payload.metadata.total_events);
  const sumW = glow.reduce((s, p) => s + p.weight, 0);
  const maxW = Math.max(...glow.map((p) => p.weight));

  /** How much of reported traffic is represented in these bins */
  const coverage = Math.min(1, sumW / totalEvents);
  /** How dominant the hottest bin is (0–1) */
  const dominance = sumW > 0 ? maxW / sumW : 0;

  const score = Math.min(
    100,
    Math.max(0, Math.round(100 * (0.35 * coverage + 0.65 * dominance))),
  );

  const bandResolved = bandFromNumericScore(score);

  return {
    score,
    label: qualityLabelForBand(bandResolved),
    band: bandResolved,
    eventWeightMatchPct: Math.round(100 * coverage),
    hotspotPeakPct: Math.round(100 * dominance),
  };
}
