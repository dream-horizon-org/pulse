import type { InteractionReportV1Wire } from "./useInteractionReport.interface";

function isReportShape(value: unknown): value is InteractionReportV1Wire {
  if (value == null || typeof value !== "object") return false;
  const o = value as Record<string, unknown>;
  return (
    typeof o.identity === "object" &&
    o.identity != null &&
    typeof o.verdict === "object" &&
    o.verdict != null
  );
}

/** Parse InteractionReportV1 from job payload, cache body, or nested `report` key. */
export function extractInteractionReport(
  data: unknown,
): InteractionReportV1Wire | null {
  if (data == null || typeof data !== "object") return null;
  const root = data as Record<string, unknown>;
  const nested = root.report;
  if (nested != null && isReportShape(nested)) {
    return nested;
  }
  if (isReportShape(root)) {
    return root;
  }
  return null;
}

export function extractCacheMeta(data: unknown): {
  cached: boolean;
  cachedAt: string | null;
} {
  if (data == null || typeof data !== "object") {
    return { cached: false, cachedAt: null };
  }
  const o = data as { cached?: boolean; cachedAt?: string };
  return {
    cached: o.cached === true,
    cachedAt: typeof o.cachedAt === "string" ? o.cachedAt : null,
  };
}
