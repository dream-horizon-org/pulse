import type {
  HeatmapCompareResponse,
  HeatmapDataResponse,
  HeatmapGlowPoint,
} from "../../screens/ScreenDetail/Heatmap/heatmap.types";
import { HEATMAP_DEMO_UNDERLAY_URL } from "../../screens/ScreenDetail/Heatmap/heatmapViz.constants";

const MOCK_UI_HASH = "a".repeat(64);
/** Mock heatmaps use bundled demo wireframe so demos never depend on external images */
const MOCK_SCREENSHOT = HEATMAP_DEMO_UNDERLAY_URL;

/** POC cap — dense but watchable in dev (DOM mode is one div per point; heatmap.js is fine with more). */
const POC_GLOW_MAX_POINTS = 1800;

function mulberry32(seed: number) {
  return function () {
    let t = (seed += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function hashScreenName(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function clamp01(n: number): number {
  return Math.min(1, Math.max(0, n));
}

function baseMetadata(screenName: string): HeatmapDataResponse["metadata"] {
  return {
    screenName,
    ui_hash: MOCK_UI_HASH,
    screenshot_url: MOCK_SCREENSHOT,
    total_events: 15_420,
    app_version: "2.1.0",
    platform: "Android",
    aspect_ratio: "19.5:9",
    created_at: "2026-03-01T12:00:00.000Z",
  };
}

type Cluster = {
  cx: number;
  cy: number;
  count: number;
  spread: number;
  wMin: number;
  wMax: number;
};

/**
 * Dense mock: bottom nav (4 “tabs”), top CTA, two content cards, FAB — capped by POC_GLOW_MAX_POINTS.
 * Coordinates normalized [0,1], origin top-left (matches UI overlay).
 */
export function heatmapMockPocDense(screenName: string): HeatmapDataResponse {
  const rand = mulberry32(hashScreenName(screenName));
  const clusters: Cluster[] = [
    { cx: 0.12, cy: 0.91, count: 1180, spread: 0.035, wMin: 3, wMax: 48 },
    { cx: 0.36, cy: 0.91, count: 1120, spread: 0.035, wMin: 3, wMax: 52 },
    { cx: 0.6, cy: 0.91, count: 1120, spread: 0.035, wMin: 3, wMax: 50 },
    { cx: 0.84, cy: 0.91, count: 980, spread: 0.03, wMin: 3, wMax: 45 },
    { cx: 0.5, cy: 0.1, count: 320, spread: 0.028, wMin: 8, wMax: 140 },
    { cx: 0.26, cy: 0.44, count: 420, spread: 0.042, wMin: 4, wMax: 62 },
    { cx: 0.74, cy: 0.41, count: 400, spread: 0.042, wMin: 4, wMax: 58 },
    { cx: 0.86, cy: 0.66, count: 460, spread: 0.032, wMin: 5, wMax: 95 },
  ];

  const planned = clusters.reduce((s, c) => s + c.count, 0);
  const scale =
    planned > POC_GLOW_MAX_POINTS ? POC_GLOW_MAX_POINTS / planned : 1;

  const glow_map: HeatmapGlowPoint[] = [];
  for (const c of clusters) {
    const n = Math.max(0, Math.floor(c.count * scale));
    for (let i = 0; i < n; i++) {
      const x = clamp01(c.cx + (rand() - 0.5) * 2 * c.spread);
      const y = clamp01(c.cy + (rand() - 0.5) * 2 * c.spread);
      const weight = c.wMin + rand() * (c.wMax - c.wMin);
      glow_map.push({
        x,
        y,
        weight: Math.round(weight * 10) / 10,
      });
    }
  }

  while (glow_map.length > POC_GLOW_MAX_POINTS) {
    glow_map.pop();
  }

  const weightSum = glow_map.reduce((s, p) => s + p.weight, 0);

  return {
    metadata: {
      ...baseMetadata(screenName),
      total_events: Math.round(weightSum) || glow_map.length,
    },
    layers: {
      glow_map,
      frustration_map: {
        rage: [
          { x: 0.36, y: 0.91, weight: 450, avg_sequence_count: 5 },
          { x: 0.86, y: 0.66, weight: 180, avg_sequence_count: 3 },
        ],
        dead: [{ x: 0.12, y: 0.2, weight: 90 }],
      },
      observability_map: {
        error_clicks: [
          { x: 0.88, y: 0.08, weight: 22, error_code: "500" },
        ],
        latency_hotspots: [
          { x: 0.28, y: 0.48, avg_latency_ms: 2100, weight: 280 },
        ],
      },
    },
  };
}

/** Small fixture for tests / legacy compare mock */
export function heatmapMockFull(screenName: string): HeatmapDataResponse {
  return {
    metadata: baseMetadata(screenName),
    layers: {
      glow_map: [
        { x: 0.45, y: 0.82, weight: 10_000 },
        { x: 0.21, y: 0.62, weight: 500 },
        { x: 0.72, y: 0.35, weight: 120 },
      ],
      frustration_map: {
        rage: [{ x: 0.45, y: 0.82, weight: 450, avg_sequence_count: 5 }],
        dead: [{ x: 0.1, y: 0.1, weight: 120 }],
      },
      observability_map: {
        error_clicks: [
          { x: 0.88, y: 0.05, weight: 15, error_code: "500" },
        ],
        latency_hotspots: [
          { x: 0.5, y: 0.5, avg_latency_ms: 2450, weight: 300 },
        ],
      },
    },
  };
}

export function heatmapMockEmpty(screenName: string): HeatmapDataResponse {
  return {
    metadata: {
      ...baseMetadata(screenName),
      total_events: 0,
    },
    layers: {
      glow_map: [],
      frustration_map: { rage: [], dead: [] },
      observability_map: { error_clicks: [], latency_hotspots: [] },
    },
  };
}

export function heatmapMockCompare(
  leftScreen: string,
  rightScreen: string,
): HeatmapCompareResponse {
  const left = heatmapMockPocDense(leftScreen);
  const right = heatmapMockPocDense(rightScreen);
  right.metadata.screenName = rightScreen;
  return {
    shared: {
      timeRange: {
        start: "2026-03-01T00:00:00.000Z",
        end: "2026-03-23T23:59:59.000Z",
      },
      filtersApplied: {},
    },
    left,
    right,
    colorScaleMax: 10_000,
  };
}

/**
 * Home feed: hero / chips / contest rows / bottom nav + FAB.
 *
 * Calibrated for the shipped quality formula (coverage = sum bin weight / total_events,
 * dominance = max bin / sum). Thousands of equal-sized micro-bins cap scores near ~35;
 * this mock uses a dominant nav aggregate plus secondary peaks and light scatter so
 * **Tap ≈ Good**, **Rage ≈ Average**, **Dead ≈ Poor** — a demo story: healthy overall
 * taps, watch rage near nav/rows, a cold search corner.
 */
export function heatmapMockHomeScreen(): HeatmapDataResponse {
  const screenName = "HomeScreen";
  const rand = mulberry32(hashScreenName("HomeScreenV3"));

  /** Session-level volume; bin weights sum slightly lower → realistic coverage < 1 */
  const total_events = 109_839;

  const coreBins: HeatmapGlowPoint[] = [
    { x: 0.5, y: 0.91, weight: 58_000 },
    { x: 0.5, y: 0.168, weight: 4_200 },
    { x: 0.28, y: 0.475, weight: 3_800 },
    { x: 0.72, y: 0.475, weight: 3_600 },
    { x: 0.5, y: 0.058, weight: 3_100 },
    { x: 0.9, y: 0.054, weight: 2_400 },
    { x: 0.18, y: 0.292, weight: 2_050 },
    { x: 0.38, y: 0.292, weight: 2_000 },
    { x: 0.58, y: 0.292, weight: 1_950 },
    { x: 0.78, y: 0.292, weight: 1_900 },
    { x: 0.28, y: 0.625, weight: 1_750 },
    { x: 0.72, y: 0.625, weight: 1_700 },
    { x: 0.5, y: 0.755, weight: 1_650 },
    { x: 0.87, y: 0.805, weight: 1_480 },
    { x: 0.1, y: 0.91, weight: 1_320 },
    { x: 0.3, y: 0.91, weight: 1_280 },
    { x: 0.7, y: 0.91, weight: 1_240 },
    { x: 0.9, y: 0.91, weight: 1_180 },
  ];

  const hazeCenters: Array<{ cx: number; cy: number; spread: number }> = [
    { cx: 0.5, cy: 0.168, spread: 0.05 },
    { cx: 0.28, cy: 0.475, spread: 0.045 },
    { cx: 0.72, cy: 0.475, spread: 0.045 },
    { cx: 0.5, cy: 0.91, spread: 0.038 },
    { cx: 0.5, cy: 0.292, spread: 0.036 },
  ];

  const glow_map: HeatmapGlowPoint[] = [...coreBins];
  let hazeCount = 0;
  const hazeTarget = 220;
  while (hazeCount < hazeTarget && glow_map.length < POC_GLOW_MAX_POINTS) {
    const c = hazeCenters[hazeCount % hazeCenters.length];
    const w = 8 + rand() * 18;
    glow_map.push({
      x: clamp01(c.cx + (rand() - 0.5) * 2 * c.spread),
      y: clamp01(c.cy + (rand() - 0.5) * 2 * c.spread),
      weight: Math.round(w * 10) / 10,
    });
    hazeCount += 1;
  }

  return {
    metadata: {
      screenName,
      ui_hash: MOCK_UI_HASH,
      screenshot_url: HEATMAP_DEMO_UNDERLAY_URL,
      total_events,
      app_version: "4.2.1",
      platform: "iOS",
      aspect_ratio: "19.5:9",
      created_at: "2026-03-28T10:00:00.000Z",
    },
    layers: {
      glow_map,
      frustration_map: {
        rage: [
          { x: 0.5, y: 0.91, weight: 18_500, avg_sequence_count: 6 },
          { x: 0.28, y: 0.475, weight: 5_200, avg_sequence_count: 4 },
          { x: 0.5, y: 0.168, weight: 2_800, avg_sequence_count: 3 },
          { x: 0.72, y: 0.475, weight: 1_900, avg_sequence_count: 3 },
        ],
        dead: [
          { x: 0.9, y: 0.054, weight: 4_800 },
          { x: 0.18, y: 0.292, weight: 2_100 },
          { x: 0.38, y: 0.292, weight: 1_800 },
        ],
      },
      observability_map: {
        error_clicks: [
          { x: 0.5, y: 0.168, weight: 38, error_code: "BANNER_403" },
          { x: 0.72, y: 0.475, weight: 24, error_code: "CARD_TELEMETRY" },
        ],
        latency_hotspots: [
          { x: 0.5, y: 0.168, avg_latency_ms: 3200, weight: 420 },
          { x: 0.28, y: 0.475, avg_latency_ms: 2650, weight: 360 },
          { x: 0.5, y: 0.91, avg_latency_ms: 890, weight: 280 },
        ],
      },
    },
  };
}

/**
 * ProductListScreen mock tuned for JoinContestButtonClick RCA: flat tap/glow signal (Poor heatmap
 * score), visible rage/dead/latency around contest rows & Join CTA — same screen as heatmap from
 * `/interaction-details/JoinContestButtonClick?tab=root-cause`.
 */
export function heatmapMockProductListJoinContest(): HeatmapDataResponse {
  const screenName = "ProductListScreen";
  const total_events = 98_500;

  /** Core bins: capped dominance (~15% of glow sum in hottest bin) → heatmap score ~0.36–0.38 (Poor). */
  const coreGlow: HeatmapGlowPoint[] = [
    { x: 0.72, y: 0.46, weight: 11_100 },
    { x: 0.42, y: 0.52, weight: 7_800 },
    { x: 0.58, y: 0.38, weight: 7_800 },
    { x: 0.3, y: 0.55, weight: 7_800 },
    { x: 0.5, y: 0.34, weight: 7_800 },
    { x: 0.84, y: 0.5, weight: 7_800 },
    { x: 0.18, y: 0.72, weight: 7_800 },
    { x: 0.62, y: 0.68, weight: 7_800 },
    { x: 0.38, y: 0.42, weight: 4_150 },
    { x: 0.76, y: 0.28, weight: 4_150 },
  ];

  const rand = mulberry32(hashScreenName("ProductListScreenJoin"));
  const haze: HeatmapGlowPoint[] = [];
  for (let i = 0; i < 160; i++) {
    haze.push({
      x: clamp01(0.08 + rand() * 0.84),
      y: clamp01(0.12 + rand() * 0.76),
      weight: Math.round((14 + rand() * 14) * 10) / 10,
    });
  }

  const glow_map = [...coreGlow, ...haze];

  return {
    metadata: {
      screenName,
      ui_hash: MOCK_UI_HASH,
      screenshot_url: MOCK_SCREENSHOT,
      total_events,
      app_version: "4.0.0",
      platform: "Android",
      aspect_ratio: "19.5:9",
      created_at: "2026-03-01T12:00:00.000Z",
    },
    layers: {
      glow_map,
      frustration_map: {
        rage: [
          { x: 0.72, y: 0.46, weight: 9_000, avg_sequence_count: 7 },
          { x: 0.42, y: 0.52, weight: 7_000, avg_sequence_count: 5 },
          { x: 0.55, y: 0.62, weight: 5_000, avg_sequence_count: 4 },
          { x: 0.28, y: 0.38, weight: 3_600, avg_sequence_count: 3 },
        ],
        dead: [
          { x: 0.5, y: 0.22, weight: 6_500 },
          { x: 0.88, y: 0.56, weight: 4_000 },
          { x: 0.22, y: 0.64, weight: 2_200 },
        ],
      },
      observability_map: {
        error_clicks: [
          { x: 0.72, y: 0.46, weight: 96, error_code: "504" },
          { x: 0.42, y: 0.52, weight: 54, error_code: "JOIN_TIMEOUT" },
        ],
        latency_hotspots: [
          /** RCA segment 2: Android 4.0.0 + OS 13 — Duration P95 */
          { x: 0.72, y: 0.46, avg_latency_ms: 6872.52, weight: 520 },
          /** RCA segment 3: iOS 4.2.0 — Duration P95 */
          { x: 0.42, y: 0.52, avg_latency_ms: 5100, weight: 380 },
          /** RCA segment 3: iOS 4.2.0 — Duration P50 (elevated vs 230 ms baseline) */
          { x: 0.5, y: 0.34, avg_latency_ms: 2340, weight: 220 },
        ],
      },
    },
  };
}

/** Resolve scenario from screenName for E2E testing */
export function resolveHeatmapData(screenName: string): HeatmapDataResponse {
  if (screenName === "__empty__") {
    return heatmapMockEmpty("__empty__");
  }
  if (screenName === "__sparse__") {
    return heatmapMockFull(screenName);
  }
  if (screenName === "HomeScreen") {
    return heatmapMockHomeScreen();
  }
  if (screenName === "ProductListScreen") {
    return heatmapMockProductListJoinContest();
  }
  return heatmapMockPocDense(screenName);
}
