/**
 * Opt-in Web Vitals stress for ecommerce-demo (URL query contract).
 *
 * Primary keys: {@code pulse_wv_stress}, {@code pulse_wv_stress_p},
 * {@code pulse_wv_stress_seed}, {@code pulse_wv_stress_severity}.
 * Short aliases: {@code _p}, {@code _seed}, {@code _severity} (same values).
 */

export type WebVitalsStressMode = "off" | "cls" | "lcp" | "fcp" | "inp" | "all";

export type WebVitalsStressSeverity = "mild" | "severe";

export type WebVitalsStressParams = {
  mode: WebVitalsStressMode;
  /** Arm rate per navigation; ignored when {@link mode} is {@code off}. */
  probability: number;
  /** Optional seed for reproducible rolls + delay bands (combine with route key in harness). */
  seed: number | undefined;
  severity: WebVitalsStressSeverity;
};

const DEFAULT_PROBABILITY = 0.35;

function parseMode(raw: string | null): WebVitalsStressMode {
  if (raw == null || raw === "") return "off";
  const v = raw.trim().toLowerCase();
  if (
    v === "off" ||
    v === "cls" ||
    v === "lcp" ||
    v === "fcp" ||
    v === "inp" ||
    v === "all"
  ) {
    return v;
  }
  return "off";
}

function parseProbability(search: URLSearchParams): number {
  const long = search.get("pulse_wv_stress_p");
  const short = search.get("_p");
  const raw = long ?? short;
  if (raw == null || raw === "") {
    return DEFAULT_PROBABILITY;
  }
  const n = Number(raw);
  if (!Number.isFinite(n)) {
    return DEFAULT_PROBABILITY;
  }
  return Math.min(1, Math.max(0, n));
}

function parseSeed(search: URLSearchParams): number | undefined {
  const long = search.get("pulse_wv_stress_seed");
  const short = search.get("_seed");
  const raw = long ?? short;
  if (raw == null || raw === "") return undefined;
  const n = Number.parseInt(raw, 10);
  return Number.isFinite(n) ? n : undefined;
}

function parseSeverity(search: URLSearchParams): WebVitalsStressSeverity {
  const long = search.get("pulse_wv_stress_severity");
  const short = search.get("_severity");
  const raw = (long ?? short ?? "").trim().toLowerCase();
  return raw === "severe" ? "severe" : "mild";
}

/**
 * Parse Web Vitals stress knobs from the current location search params.
 */
export function parseWebVitalsStressSearchParams(
  search: URLSearchParams,
): WebVitalsStressParams {
  const mode = parseMode(search.get("pulse_wv_stress"));
  const probability = parseProbability(search);
  const seed = parseSeed(search);
  const severity = parseSeverity(search);
  return { mode, probability, seed, severity };
}
