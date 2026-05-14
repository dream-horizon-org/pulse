import type {
  ScreenVital,
  TrendPoint,
  VitalSummary,
  WebVitalsByScreenResponse,
  WebVitalsSummaryResponse,
  WebVitalsTrendResponse,
} from "./WebVitals.interface";
import type {
  ScreenVitalWire,
  TrendPointWire,
  VitalSummaryWire,
  WebVitalsByScreenWire,
  WebVitalsSummaryWire,
  WebVitalsTrendWire,
} from "./WebVitalsWire.types";

type NumericWire = string | number | bigint | null | undefined;

function num(v: NumericWire, fallback = 0): number {
  if (v === null || v === undefined) {
    return fallback;
  }
  if (typeof v === "bigint") {
    const n = Number(v);
    return Number.isFinite(n) ? n : fallback;
  }
  if (typeof v === "number") {
    return Number.isFinite(v) ? v : fallback;
  }
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

export function normalizeScreenVital(row: ScreenVitalWire): ScreenVital {
  return {
    screenName: String(row.screenName ?? row.screen_name ?? ""),
    p75: num(row.p75),
    totalCount: Math.trunc(num(row.totalCount ?? row.total_count)),
    goodPct: num(row.goodPct ?? row.good_pct),
  };
}

export function normalizeVitalSummary(row: VitalSummaryWire): VitalSummary {
  return {
    name: String(row.name ?? ""),
    p75: num(row.p75),
    goodPct: num(row.goodPct ?? row.good_pct),
    needsImprovementPct: num(
      row.needsImprovementPct ?? row.needs_improvement_pct,
    ),
    poorPct: num(row.poorPct ?? row.poor_pct),
    totalCount: Math.trunc(num(row.totalCount ?? row.total_count)),
  };
}

export function normalizeTrendPoint(row: TrendPointWire): TrendPoint {
  return {
    bucket: String(row.bucket ?? ""),
    p75: num(row.p75),
  };
}

export function normalizeWebVitalsSummaryResponse(
  data: WebVitalsSummaryWire | null | undefined,
): WebVitalsSummaryResponse | null {
  if (data == null) {
    return null;
  }
  const vitals = data.vitals;
  if (!Array.isArray(vitals)) {
    return { vitals: [] };
  }
  return {
    vitals: vitals.map((v) => normalizeVitalSummary(v)),
  };
}

export function normalizeWebVitalsTrendResponse(
  data: WebVitalsTrendWire | null | undefined,
): WebVitalsTrendResponse | null {
  if (data == null) {
    return null;
  }
  const points = data.points;
  if (!Array.isArray(points)) {
    return { points: [] };
  }
  return {
    points: points.map((p) => normalizeTrendPoint(p)),
  };
}

export function normalizeWebVitalsByScreenResponse(
  data: WebVitalsByScreenWire | null | undefined,
): WebVitalsByScreenResponse | null {
  if (data == null) {
    return null;
  }
  const screens = data.screens;
  if (!Array.isArray(screens)) {
    return { screens: [] };
  }
  return {
    screens: screens.map((s) => normalizeScreenVital(s)),
  };
}
