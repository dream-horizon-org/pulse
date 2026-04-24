export type MetricPolarity = "higher_is_better" | "higher_is_worse" | "neutral";

export type MetricValueTone = "good" | "bad" | "neutral";

export type MetricDisplayHints = {
  valueDisplay?: string;
  baselineDisplay?: string;
  deltaDisplay?: string;
};

const EPS = 1e-9;
const DELTA_EPS = 0.005;

/**
 * LLMs and some locales emit Unicode minus (U+2212) or en dash instead of ASCII `-`.
 * Our numeric regexes only match ASCII `+`/`-`, so failing to normalize yields null parses
 * → neutral tone → grey Value/Delta.
 */
const normalizeSignedNumberText = (raw: string): string =>
  String(raw)
    .replace(/\u2212/g, "-")
    .replace(/\u2013/g, "-")
    .replace(/\u2014/g, "-");

/** Aligned with backend `RootCauseMetricsRegistry` metric ids. */
const METRIC_POLARITY: Readonly<Record<string, MetricPolarity>> = {
  volume: "neutral",
  apdex: "higher_is_better",
  error_rate: "higher_is_worse",
  poor_user_pct: "higher_is_worse",
  duration_p50: "higher_is_worse",
  duration_p95: "higher_is_worse",
  crash_rate: "higher_is_worse",
  anr_rate: "higher_is_worse",
  frozen_frame_rate: "higher_is_worse",
  slow_frame_rate: "higher_is_worse",
};

export const getMetricPolarity = (metricId: string): MetricPolarity =>
  METRIC_POLARITY[metricId] ?? "neutral";

const parseMsNumber = (raw: string): number | null => {
  const m = normalizeSignedNumberText(raw)
    .trim()
    .replace(/,/g, "")
    .match(/^([+-]?\d+(?:\.\d+)?)\s*ms$/i);
  if (m == null) return null;
  const n = parseFloat(m[1]);
  return Number.isFinite(n) ? n : null;
};

const parsePercentOrNumber = (raw: string): number | null => {
  const s = normalizeSignedNumberText(raw).trim().replace(/,/g, "");
  const pct = s.match(/^([+-]?\d+(?:\.\d+)?)\s*%$/);
  if (pct != null) {
    const n = parseFloat(pct[1]);
    return Number.isFinite(n) ? n : null;
  }
  const plain = s.match(/^([+-]?\d+(?:\.\d+)?)$/);
  if (plain != null) {
    const n = parseFloat(plain[1]);
    return Number.isFinite(n) ? n : null;
  }
  return null;
};

const parseDisplayForMetric = (
  metricId: string,
  display: string | null | undefined,
): number | null => {
  if (display == null) return null;
  const d = String(display).trim();
  if (metricId === "duration_p50" || metricId === "duration_p95") {
    return parseMsNumber(d);
  }
  return parsePercentOrNumber(d);
};

/** First signed percent in the string (e.g. "-99.3%" or "+448.8%"). */
const parseDeltaPercentLoose = (
  raw: string | null | undefined,
): number | null => {
  if (raw == null) return null;
  const m = normalizeSignedNumberText(String(raw)).match(
    /([+-]?\d+(?:\.\d+)?)\s*%/,
  );
  if (m == null) return null;
  const n = parseFloat(m[1]);
  return Number.isFinite(n) ? n : null;
};

/** Above this absolute delta (%), RCA segment tables show infinity instead of the raw value. */
export const RCA_DELTA_DISPLAY_INFINITY_THRESHOLD_PCT = 9999;

/**
 * Formats `delta_display` for the RCA metrics table: huge percent deltas render as ±∞%.
 * Uses the same loose percent parsing as tone logic (commas, Unicode minus).
 */
export const formatRcaDeltaDisplay = (
  deltaDisplay: string | null | undefined,
): string => {
  if (deltaDisplay == null) return "";
  const raw = String(deltaDisplay);
  const n = parseDeltaPercentLoose(raw);
  if (n == null || Math.abs(n) <= RCA_DELTA_DISPLAY_INFINITY_THRESHOLD_PCT) {
    return raw;
  }
  return n > 0 ? "+∞%" : "-∞%";
};

const toneFromDiff = (
  polarity: MetricPolarity,
  diff: number,
): MetricValueTone => {
  if (Math.abs(diff) < EPS) return "neutral";
  const higher = diff > EPS;
  if (polarity === "higher_is_worse") {
    return higher ? "bad" : "good";
  }
  return higher ? "good" : "bad";
};

export const getMetricValueTone = (
  metricId: string,
  valueNumber: number | null | undefined,
  baselineNumber: number | null | undefined,
  hints?: MetricDisplayHints,
): MetricValueTone => {
  const polarity = getMetricPolarity(metricId);
  if (polarity === "neutral") return "neutral";

  let valueParsed = valueNumber ?? null;
  let baselineParsed = baselineNumber ?? null;

  if (valueParsed == null || baselineParsed == null) {
    const fromValue = parseDisplayForMetric(metricId, hints?.valueDisplay);
    const fromBaseline = parseDisplayForMetric(
      metricId,
      hints?.baselineDisplay,
    );
    if (fromValue != null && fromBaseline != null) {
      valueParsed = fromValue;
      baselineParsed = fromBaseline;
    }
  }

  if (valueParsed != null && baselineParsed != null) {
    return toneFromDiff(polarity, valueParsed - baselineParsed);
  }

  const deltaNum = parseDeltaPercentLoose(hints?.deltaDisplay);
  if (deltaNum == null || Math.abs(deltaNum) < DELTA_EPS) {
    return "neutral";
  }
  const positiveDelta = deltaNum > DELTA_EPS;
  if (polarity === "higher_is_worse") {
    return positiveDelta ? "bad" : "good";
  }
  return positiveDelta ? "good" : "bad";
};
