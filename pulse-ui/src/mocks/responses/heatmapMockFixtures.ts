import type {
  HeatmapCompareResponse,
  HeatmapDataResponse,
  HeatmapDataWireResponse,
  HeatmapGlowPoint,
  HeatmapInteractionMapLayer,
  HeatmapInteractionsMetadataRow,
} from "../../screens/ScreenDetail/Heatmap/heatmap.types";
import { aggregatePulseInteractionsForScreen } from "../../screens/ScreenDetail/Heatmap/heatmapKeyLensAggregates";
import { normalizeHeatmapWireResponse } from "../../screens/ScreenDetail/Heatmap/heatmapWireNormalize";
import { HEATMAP_DEFAULT_UNDERLAY_URL } from "../../screens/ScreenDetail/Heatmap/heatmapViz.constants";

const MOCK_UI_HASH = "a".repeat(64);
const MOCK_SCREENSHOT = HEATMAP_DEFAULT_UNDERLAY_URL;

/** Dense POC mock size (backend contract max for `glow_map` is 10k). */
const POC_GLOW_MAX_POINTS = 10_000;

const GOLDEN_RATIO_FRAC = 0.6180339887498949;

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

/**
 * Per–browser-tab salt so mock heatmaps vary between sessions/manual QA runs
 * while staying stable for the lifetime of the tab (fewer flaky surprises when
 * refreshing a single screen).
 */
function heatmapMockSessionSalt(): number {
  try {
    if (typeof sessionStorage === "undefined") {
      return 0;
    }
    const key = "pulseHeatmapMockSalt";
    let s = sessionStorage.getItem(key);
    if (s == null) {
      s = String((Math.random() * 0x7fffffff) | 0);
      sessionStorage.setItem(key, s);
    }
    return Number(s) >>> 0;
  } catch {
    return 0;
  }
}

function clamp01(n: number): number {
  return Math.min(1, Math.max(0, n));
}

/** XOR-in unpredictable bits so each mock response differs (QA), without breaking SSR. */
function mockFetchEntropy(): number {
  try {
    const a = new Uint32Array(1);
    globalThis.crypto?.getRandomValues?.(a);
    if (a[0]) return a[0];
  } catch {
    /* ignore */
  }
  return (Math.random() * 0x7fffffff) >>> 0;
}

function baseMetadata(screenName: string): HeatmapDataResponse["metadata"] {
  return {
    screenName,
    ui_hash: MOCK_UI_HASH,
    screenshot_url: MOCK_SCREENSHOT,
    screenshot_urls: [
      MOCK_SCREENSHOT,
      "https://placehold.co/390x844/334155/f1f5f9/png?text=Capture+2",
      "https://placehold.co/390x844/0f172a/e2e8f0/png?text=Capture+3",
    ],
    total_events: 15_420,
    app_version: "2.1.0",
    platform: "Android",
    aspect_ratio: "19.5:9",
    created_at: "2026-03-01T12:00:00.000Z",
  };
}

/** Mock Key-actions layer: normalized bounds + Pulse interaction scores per element. */
function mockInteractionsMetadataFromMap(
  layer: HeatmapInteractionMapLayer,
): HeatmapInteractionsMetadataRow[] {
  return aggregatePulseInteractionsForScreen(layer.regions).map((r) => ({
    interaction_name: r.displayName,
    avg_score: r.score01,
  }));
}

export function heatmapMockInteractionMap(): HeatmapInteractionMapLayer {
  return {
    regions: [
      {
        element_id: "hero_cta",
        minX: 0.14,
        minY: 0.38,
        maxX: 0.86,
        maxY: 0.44,
        interaction_scores: [
          {
            interaction_id: "pi_cta_primary",
            name: "Primary CTA",
            score: 0.88,
          },
          {
            interaction_id: "pi_cta_second",
            name: "Secondary funnel",
            score: 0.71,
          },
        ],
      },
      {
        element_id: "list_row_2",
        minX: 0.08,
        minY: 0.52,
        maxX: 0.92,
        maxY: 0.6,
        avg_score: 0.64,
        interaction_scores: [
          {
            interaction_id: "pi_open_detail",
            name: "Open detail",
            score: 0.72,
          },
          {
            interaction_id: "pi_quick_action",
            name: "Quick action",
            score: 0.55,
          },
        ],
      },
      {
        element_id: "nav_tab_home",
        minX: 0.02,
        minY: 0.88,
        maxX: 0.22,
        maxY: 0.97,
        interaction_scores: [
          {
            interaction_id: "pi_tab_home",
            name: "Home tab",
            score: 0.93,
          },
        ],
      },
    ],
  };
}

/**
 * Many small hotspots spread across the frame. Ambient sprinkle is capped so
 * heatmap.js doesn’t stack low-weight blobs into a gray/blue sheet.
 */
function buildDistributedGlowMap(rand: () => number, seed: number): HeatmapGlowPoint[] {
  const maxPoints = POC_GLOW_MAX_POINTS;
  const glow_map: HeatmapGlowPoint[] = [];
  let remaining = maxPoints;

  const layoutRoll = rand();
  const rowBias = layoutRoll < 0.22;
  const diffuseLayout = layoutRoll > 0.78;
  const nAnchors = 20 + (seed % 7) + Math.floor(rand() * 16);
  const anchorPortion = 0.88 + rand() * 0.1;
  const targetAnchorPoints = Math.min(
    Math.floor(maxPoints * anchorPortion),
    Math.max(0, remaining),
  );
  const perAnchorBase =
    nAnchors > 0 ? Math.max(10, Math.floor(targetAnchorPoints / nAnchors)) : 0;

  for (let k = 0; k < nAnchors && remaining > 0; k++) {
    if (rand() < 0.08) {
      continue;
    }

    let u = (k * GOLDEN_RATIO_FRAC + rand() * 0.22) % 1;
    let v = (k * GOLDEN_RATIO_FRAC * GOLDEN_RATIO_FRAC + rand() * 0.24) % 1;

    if (rowBias) {
      const band = Math.floor(rand() * 9) / 9;
      v = clamp01(0.06 + band * 0.86 + (rand() - 0.5) * 0.045);
      u = (u * 0.65 + rand() * 0.35) % 1;
    }

    const cx = clamp01(0.03 + u * 0.94 + (rand() - 0.5) * 0.06);
    const cy = clamp01(0.03 + v * 0.94 + (rand() - 0.5) * 0.06);
    const spreadBase = diffuseLayout ? 0.018 + rand() * 0.048 : 0.009 + rand() * 0.038;
    const spread = spreadBase * (0.65 + rand() * 0.75);

    let n = Math.min(
      remaining,
      Math.floor(perAnchorBase * (0.38 + rand() * 1.35)),
    );
    n = Math.max(4, n);

    const hotSpot = rand() < (0.1 + rand() * 0.12);
    const wMin = hotSpot ? 12 + rand() * 42 : 3 + rand() * 22;
    const wMax = hotSpot
      ? wMin + 60 + rand() * 220
      : wMin + 10 + rand() * 95;

    for (let i = 0; i < n; i++) {
      const x = clamp01(cx + (rand() - 0.5) * 2 * spread);
      const y = clamp01(cy + (rand() - 0.5) * 2 * spread);
      let weight = wMin + rand() * (wMax - wMin);
      weight *= 0.82 + rand() * 0.38;
      glow_map.push({
        x,
        y,
        weight: Math.max(0, Math.round(weight)),
      });
    }
    remaining -= n;
  }

  const ambientFrac = 0.04 + rand() * 0.055;
  const ambientCap = Math.min(remaining, Math.round(maxPoints * ambientFrac));
  for (let a = 0; a < ambientCap; a++) {
    const x = clamp01(0.02 + rand() * 0.96);
    const y = clamp01(0.02 + rand() * 0.96);
    const weight = (0.8 + rand() * 14) * (0.75 + rand() * 0.5);
    glow_map.push({
      x,
      y,
      weight: Math.max(0, Math.round(weight)),
    });
  }

  return glow_map;
}

/**
 * Dense mock: wide, many-peak + ambient field — capped by POC_GLOW_MAX_POINTS.
 * Coordinates normalized [0,1], origin top-left (matches UI overlay).
 */
export function heatmapMockPocDense(screenName: string): HeatmapDataResponse {
  const salt = heatmapMockSessionSalt();
  const seed = hashScreenName(screenName) ^ salt ^ mockFetchEntropy();
  const rand = mulberry32(seed ^ 0x51bec0de);
  const glow_map = buildDistributedGlowMap(rand, seed);

  const glowWeightSum = glow_map.reduce((s, p) => s + p.weight, 0);
  const total_events = Math.max(12_000, Math.round(glowWeightSum * 0.76));
  const frustrationBudget = Math.max(400, Math.round(total_events * 0.13));
  const rageA = Math.round(frustrationBudget * 0.52);
  const rageB = Math.round(frustrationBudget * 0.31);
  const deadW = Math.max(80, frustrationBudget - rageA - rageB);
  const interactionMap = heatmapMockInteractionMap();

  const wire: HeatmapDataWireResponse = {
    metadata: {
      ...baseMetadata(screenName),
      total_events,
    },
    layers: {
      glow_map,
      frustration_map: {
        rage_taps: [
          {
            x: clamp01(0.36 + (rand() - 0.5) * 0.04),
            y: clamp01(0.91 + (rand() - 0.5) * 0.03),
            weight: rageA,
            avg_sequence_count: 4 + Math.round(rand() * 4),
          },
          {
            x: clamp01(0.86 + (rand() - 0.5) * 0.04),
            y: clamp01(0.66 + (rand() - 0.5) * 0.04),
            weight: rageB,
            avg_sequence_count: 2 + Math.round(rand() * 4),
          },
        ],
        dead_taps: [
          {
            x: clamp01(0.12 + (rand() - 0.5) * 0.05),
            y: clamp01(0.2 + (rand() - 0.5) * 0.05),
            weight: deadW,
          },
        ],
      },
      observability_map: {
        error_clicks: [
          { x: 0.88, y: 0.08, weight: 22, error_code: "500" },
        ],
        latency_hotspots: [
          { x: 0.28, y: 0.48, avg_latency_ms: 2100, weight: 280 },
        ],
      },
      interaction_map: interactionMap,
    },
    interactions_metadata: mockInteractionsMetadataFromMap(interactionMap),
  };

  return normalizeHeatmapWireResponse(wire);
}

/** Small fixture for tests / legacy compare mock */
export function heatmapMockFull(screenName: string): HeatmapDataResponse {
  const interactionMap = heatmapMockInteractionMap();
  return normalizeHeatmapWireResponse({
    metadata: baseMetadata(screenName),
    layers: {
      glow_map: [
        { x: 0.45, y: 0.82, weight: 10_000 },
        { x: 0.21, y: 0.62, weight: 500 },
        { x: 0.72, y: 0.35, weight: 120 },
      ],
      frustration_map: {
        rage_taps: [
          { x: 0.45, y: 0.82, weight: 450, avg_sequence_count: 5 },
        ],
        dead_taps: [{ x: 0.1, y: 0.1, weight: 120 }],
      },
      observability_map: {
        error_clicks: [
          { x: 0.88, y: 0.05, weight: 15, error_code: "500" },
        ],
        latency_hotspots: [
          { x: 0.5, y: 0.5, avg_latency_ms: 2450, weight: 300 },
        ],
      },
      interaction_map: interactionMap,
    },
    interactions_metadata: mockInteractionsMetadataFromMap(interactionMap),
  });
}

/**
 * Dense heatmap with screenshot URLs stripped — UI “no capture” path.
 * QA: magic screen `__no_screenshots__` or mock scenario toolbar.
 */
export function heatmapMockNoScreenshots(): HeatmapDataResponse {
  const dense = heatmapMockPocDense("__no_screenshots__");
  return {
    ...dense,
    metadata: {
      ...dense.metadata,
      screenshot_url: "",
      screenshot_urls: [],
    },
  };
}

/**
 * Successful response with no bins — no Key actions layer (heatmap-only API).
 * QA: magic screen `__empty__` or mock scenario toolbar.
 */
export function heatmapMockEmpty(screenName: string): HeatmapDataResponse {
  return normalizeHeatmapWireResponse({
    metadata: {
      ...baseMetadata(screenName),
      total_events: 0,
    },
    layers: {
      glow_map: [],
      frustration_map: { rage_taps: [], dead_taps: [] },
      observability_map: { error_clicks: [], latency_hotspots: [] },
    },
  });
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

/** Optional GET/POST hints so mocks reflect filter changes in QA. */
export type HeatmapMockRequestHints = {
  app_version?: string | null;
  platform?: string | null;
  region?: string | null;
  from?: string | null;
  to?: string | null;
  breakpoint?: string | null;
};

function hashHints(h: HeatmapMockRequestHints): number {
  const s = [
    h.app_version,
    h.platform,
    h.region,
    h.from,
    h.to,
    h.breakpoint,
  ].join("|");
  return hashScreenName(s);
}

function applyHintsToPayload(
  data: HeatmapDataResponse,
  hints: HeatmapMockRequestHints,
): HeatmapDataResponse {
  const salt = hashHints(hints);
  const factor = 0.82 + (salt % 35) / 100;
  const bpTrim = hints.breakpoint?.trim();
  const bpFactor =
    bpTrim != null && bpTrim !== ""
      ? 0.97 + (hashScreenName(bpTrim) % 7) / 200
      : 1;

  return {
    ...data,
    metadata: {
      ...data.metadata,
      total_events: Math.max(
        100,
        Math.round(data.metadata.total_events * factor * bpFactor),
      ),
      ...(hints.platform ? { platform: hints.platform } : {}),
      ...(hints.app_version ? { app_version: hints.app_version } : {}),
    },
  };
}

/**
 * Resolve scenario from `screenName` (query/body) for mocks:
 * - `__empty__` — no glow/frustration bins (empty heatmap UI)
 * - `__error__` — 500 from MockResponseGenerator (not handled here)
 * - `__sparse__` — small fixed fixture
 * - `__no_screenshots__` — full bins, empty screenshot list
 * - `__mock_compare_b__` — alternate dense seed for compare column B
 * - anything else — dense POC heatmap
 */
export function resolveHeatmapData(
  screenName: string,
  hints?: HeatmapMockRequestHints,
): HeatmapDataResponse {
  let data: HeatmapDataResponse;
  if (screenName === "__empty__") {
    data = heatmapMockEmpty("__empty__");
  } else if (screenName === "__no_screenshots__") {
    data = heatmapMockNoScreenshots();
  } else if (screenName === "__sparse__") {
    data = heatmapMockFull(screenName);
  } else {
    data = heatmapMockPocDense(screenName);
  }

  if (
    hints &&
    (hints.platform ||
      hints.app_version ||
      hints.region ||
      hints.from ||
      hints.to ||
      hints.breakpoint?.trim())
  ) {
    data = applyHintsToPayload(data, hints);
  }

  return data;
}
