import type { HeatmapDataResponse } from "./heatmap.types";
import type { HeatmapFocusLens, HeatmapSignal } from "./heatmapPanelUtils";
import type { HeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapAggregatesHeatmapLensPanel } from "./HeatmapAggregatesHeatmapLensPanel";
import { HeatmapAggregatesKeyLensPanel } from "./HeatmapAggregatesKeyLensPanel";

export interface HeatmapAggregatesPanelProps {
  payload: HeatmapDataResponse;
  signal: HeatmapSignal;
  qualityMetrics: HeatmapQualityMetrics;
  focusLens: HeatmapFocusLens;
}

/** Right rail: density stats vs Pulse interaction map, depending on Focus. */
export function HeatmapAggregatesPanel({
  payload,
  signal,
  qualityMetrics,
  focusLens,
}: HeatmapAggregatesPanelProps) {
  if (focusLens === "key") {
    return <HeatmapAggregatesKeyLensPanel payload={payload} />;
  }
  return (
    <HeatmapAggregatesHeatmapLensPanel
      payload={payload}
      signal={signal}
      qualityMetrics={qualityMetrics}
    />
  );
}
