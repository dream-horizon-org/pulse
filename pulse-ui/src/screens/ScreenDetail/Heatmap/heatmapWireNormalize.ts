import type {
  HeatmapDataResponse,
  HeatmapDataWireLayers,
  HeatmapDataWireResponse,
  HeatmapFrustrationPoint,
  HeatmapInteractionsMetadataRow,
} from "./heatmap.types";

function asFrustrationPoints(arr: unknown): HeatmapFrustrationPoint[] {
  if (!Array.isArray(arr)) return [];
  return arr as HeatmapFrustrationPoint[];
}

/** Legacy wire shape: some payloads nested `interactions_metadata` under `layers`. */
type HeatmapDataWireLayersLegacy = HeatmapDataWireLayers & {
  interactions_metadata?: unknown;
};

function wireInteractionsMetadataRaw(
  wire: HeatmapDataWireResponse,
): unknown[] | undefined {
  const top = wire.interactions_metadata;
  if (top != null && Array.isArray(top)) return top;

  const legacy = (wire.layers as HeatmapDataWireLayersLegacy)
    .interactions_metadata;
  if (legacy != null && Array.isArray(legacy)) return legacy;

  return undefined;
}

function mapWireInteractionMetadataRow(
  r: Record<string, unknown>,
): HeatmapInteractionsMetadataRow {
  const name = String(r.interaction_name ?? "");
  const raw = r.avg_score;
  const avg_score =
    raw === null || raw === undefined || raw === ""
      ? null
      : Number(raw);
  return {
    interaction_name: name,
    avg_score: Number.isFinite(avg_score) ? avg_score : null,
  };
}

/**
 * Maps API (wire) JSON → in-app `HeatmapDataResponse` (rage/dead frustr. keys).
 * `interactions_metadata` stays **top-level** on the result (legacy wire may nest under `layers` only as input).
 */
export function normalizeHeatmapWireResponse(
  wire: HeatmapDataWireResponse,
): HeatmapDataResponse {
  const fm = wire.layers?.frustration_map;
  const rage = asFrustrationPoints(fm?.rage_taps);
  const dead = asFrustrationPoints(fm?.dead_taps);

  const rawMeta = wireInteractionsMetadataRaw(wire);
  const metaRows: HeatmapInteractionsMetadataRow[] = [];
  if (rawMeta != null) {
    for (const item of rawMeta) {
      if (item != null && typeof item === "object" && !Array.isArray(item)) {
        metaRows.push(
          mapWireInteractionMetadataRow(item as Record<string, unknown>),
        );
      }
    }
  }

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
    },
    ...(metaRows.length > 0 ? { interactions_metadata: metaRows } : {}),
  };
}

export function normalizeHeatmapWirePayload(
  raw: unknown,
): HeatmapDataResponse {
  return normalizeHeatmapWireResponse(raw as HeatmapDataWireResponse);
}
