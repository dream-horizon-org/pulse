import type {
  HeatmapDataResponse,
  HeatmapDataWireResponse,
  HeatmapFrustrationPoint,
  HeatmapInteractionsMetadataRow,
} from "./heatmap.types";

function asFrustrationPoints(arr: unknown): HeatmapFrustrationPoint[] {
  if (!Array.isArray(arr)) return [];
  return arr as HeatmapFrustrationPoint[];
}

/**
 * Maps API (wire) JSON → in-app `HeatmapDataResponse` (rage/dead frustr. keys,
 * optional `layers.interactions_metadata` kept for the right rail).
 */
export function normalizeHeatmapWireResponse(
  wire: HeatmapDataWireResponse,
): HeatmapDataResponse {
  const fm = wire.layers?.frustration_map;
  const rage = asFrustrationPoints(fm?.rage_taps);
  const dead = asFrustrationPoints(fm?.dead_taps);
  const metaRows: HeatmapInteractionsMetadataRow[] =
    wire.layers?.interactions_metadata != null &&
    Array.isArray(wire.layers.interactions_metadata)
      ? wire.layers.interactions_metadata.map((r) => ({
          interaction_name: String(r.interaction_name ?? ""),
          avg_score: Number(r.avg_score),
        }))
      : [];

  return {
    metadata: wire.metadata,
    layers: {
      glow_map: wire.layers?.glow_map ?? [],
      frustration_map: { rage, dead },
      observability_map: {
        error_clicks: wire.layers?.observability_map?.error_clicks ?? [],
        latency_hotspots: wire.layers?.observability_map?.latency_hotspots ?? [],
      },
      ...(wire.layers?.interaction_map != null
        ? { interaction_map: wire.layers.interaction_map }
        : {}),
      ...(metaRows.length > 0 ? { interactions_metadata: metaRows } : {}),
    },
  };
}

export function normalizeHeatmapWirePayload(
  raw: unknown,
): HeatmapDataResponse {
  return normalizeHeatmapWireResponse(raw as HeatmapDataWireResponse);
}
