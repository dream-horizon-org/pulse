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
 * Home feed layout: search / hero / category chips / contest cards / promo / bottom nav + FAB.
 * Coordinates normalized [0,1]; tuned so heat reads as a typical fantasy-sports home screen.
 */
export function heatmapMockHomeScreen(): HeatmapDataResponse {
  const screenName = "HomeScreen";
  const rand = mulberry32(hashScreenName("HomeScreenV2"));
  const clusters: Cluster[] = [
    { cx: 0.5, cy: 0.058, count: 380, spread: 0.024, wMin: 14, wMax: 98 },
    { cx: 0.9, cy: 0.054, count: 220, spread: 0.02, wMin: 10, wMax: 62 },
    { cx: 0.5, cy: 0.168, count: 1020, spread: 0.056, wMin: 22, wMax: 195 },
    { cx: 0.18, cy: 0.292, count: 340, spread: 0.032, wMin: 12, wMax: 88 },
    { cx: 0.38, cy: 0.292, count: 330, spread: 0.032, wMin: 12, wMax: 86 },
    { cx: 0.58, cy: 0.292, count: 325, spread: 0.032, wMin: 12, wMax: 84 },
    { cx: 0.78, cy: 0.292, count: 318, spread: 0.032, wMin: 12, wMax: 82 },
    { cx: 0.28, cy: 0.475, count: 920, spread: 0.05, wMin: 18, wMax: 205 },
    { cx: 0.72, cy: 0.475, count: 900, spread: 0.05, wMin: 18, wMax: 200 },
    { cx: 0.28, cy: 0.625, count: 560, spread: 0.046, wMin: 12, wMax: 128 },
    { cx: 0.72, cy: 0.625, count: 548, spread: 0.046, wMin: 12, wMax: 125 },
    { cx: 0.5, cy: 0.755, count: 510, spread: 0.042, wMin: 14, wMax: 138 },
    { cx: 0.1, cy: 0.91, count: 920, spread: 0.034, wMin: 5, wMax: 55 },
    { cx: 0.3, cy: 0.91, count: 1120, spread: 0.034, wMin: 6, wMax: 68 },
    { cx: 0.5, cy: 0.91, count: 1380, spread: 0.036, wMin: 10, wMax: 102 },
    { cx: 0.7, cy: 0.91, count: 1080, spread: 0.034, wMin: 6, wMax: 62 },
    { cx: 0.9, cy: 0.91, count: 960, spread: 0.034, wMin: 5, wMax: 54 },
    { cx: 0.87, cy: 0.805, count: 310, spread: 0.03, wMin: 16, wMax: 118 },
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
      screenName,
      ui_hash: MOCK_UI_HASH,
      screenshot_url: HEATMAP_DEMO_UNDERLAY_URL,
      total_events: Math.round(weightSum) || glow_map.length,
      app_version: "4.2.1",
      platform: "iOS",
      aspect_ratio: "19.5:9",
      created_at: "2026-03-28T10:00:00.000Z",
    },
    layers: {
      glow_map,
      frustration_map: {
        rage: [
          { x: 0.5, y: 0.91, weight: 520, avg_sequence_count: 6 },
          { x: 0.28, y: 0.475, weight: 340, avg_sequence_count: 4 },
          { x: 0.5, y: 0.168, weight: 210, avg_sequence_count: 3 },
        ],
        dead: [
          { x: 0.9, y: 0.054, weight: 95 },
          { x: 0.18, y: 0.292, weight: 72 },
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
 * Rich ProductListScreen mock: strong tap/rage/latency around contest rows & Join CTA
 * (aligns with JoinContestButtonClick RCA — same screen as `/screens/ProductListScreen?tab=heatmap`).
 */
export function heatmapMockProductListJoinContest(): HeatmapDataResponse {
  const base = heatmapMockPocDense("ProductListScreen");
  const joinCtaCluster: Cluster[] = [
    { cx: 0.72, cy: 0.46, count: 980, spread: 0.038, wMin: 14, wMax: 210 },
    { cx: 0.42, cy: 0.52, count: 720, spread: 0.042, wMin: 10, wMax: 165 },
    { cx: 0.88, cy: 0.44, count: 540, spread: 0.03, wMin: 8, wMax: 120 },
  ];
  const rand = mulberry32(hashScreenName("ProductListScreenJoin"));
  const extraGlow: HeatmapGlowPoint[] = [];
  for (const c of joinCtaCluster) {
    const n = Math.min(c.count, 420);
    for (let i = 0; i < n; i++) {
      const x = clamp01(c.cx + (rand() - 0.5) * 2 * c.spread);
      const y = clamp01(c.cy + (rand() - 0.5) * 2 * c.spread);
      const weight = c.wMin + rand() * (c.wMax - c.wMin);
      extraGlow.push({ x, y, weight: Math.round(weight * 10) / 10 });
    }
  }
  const glow_map = [...base.layers.glow_map, ...extraGlow];
  while (glow_map.length > POC_GLOW_MAX_POINTS) {
    glow_map.pop();
  }
  const weightSum = glow_map.reduce((s, p) => s + p.weight, 0);

  return {
    metadata: {
      ...base.metadata,
      total_events: Math.round(weightSum) || glow_map.length,
      app_version: "4.0.0",
      platform: "Android",
    },
    layers: {
      glow_map,
      frustration_map: {
        rage: [
          ...base.layers.frustration_map.rage,
          {
            x: 0.72,
            y: 0.46,
            weight: 620,
            avg_sequence_count: 6,
          },
          {
            x: 0.42,
            y: 0.52,
            weight: 410,
            avg_sequence_count: 4,
          },
        ],
        dead: [
          ...base.layers.frustration_map.dead,
          { x: 0.72, y: 0.44, weight: 140 },
        ],
      },
      observability_map: {
        error_clicks: [
          ...base.layers.observability_map.error_clicks,
          { x: 0.72, y: 0.46, weight: 96, error_code: "504" },
          { x: 0.42, y: 0.52, weight: 54, error_code: "JOIN_TIMEOUT" },
        ],
        latency_hotspots: [
          ...base.layers.observability_map.latency_hotspots,
          {
            x: 0.72,
            y: 0.46,
            avg_latency_ms: 6872,
            weight: 520,
          },
          {
            x: 0.42,
            y: 0.52,
            avg_latency_ms: 5100,
            weight: 380,
          },
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
