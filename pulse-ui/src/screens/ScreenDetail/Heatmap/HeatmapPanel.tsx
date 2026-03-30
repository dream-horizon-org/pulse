import { useMemo, useState } from "react";
import { getHeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapComparePanel } from "./HeatmapComparePanel";
import { HeatmapMainCard } from "./HeatmapMainCard";
import { useHeatmapData } from "../../../hooks/useHeatmapData";
import { useFilterStore } from "../../../stores/useFilterStore";
import {
  compareSharedWeightMax,
  glowLayerForSignal,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import type { HeatmapPanelProps } from "./heatmapPanel.types";

export type { HeatmapPanelProps } from "./heatmapPanel.types";

/**
 * Heatmap tab — Summary card (metrics + coverage), Filters & maps card (signal + compare + one or two maps).
 */
export function HeatmapPanel({
  screenName,
  startTime,
  endTime,
  engagement,
}: HeatmapPanelProps) {
  const { filterValues } = useFilterStore();
  const [compareEnabled, setCompareEnabled] = useState(false);
  const [compareScreenName, setCompareScreenName] = useState("HomeScreen");
  const [signal, setSignal] = useState<HeatmapSignal>("tap");
  const heatmapRequestFilters = useMemo(
    () => ({
      app_version: filterValues?.APP_VERSION?.trim() || undefined,
      platform: filterValues?.PLATFORM?.trim() || undefined,
    }),
    [filterValues],
  );

  const compareSliceReady =
    compareEnabled && !!compareScreenName.trim();

  const heatmapQuery = useHeatmapData({
    screenName,
    startTime,
    endTime,
    ...heatmapRequestFilters,
    enabled: !compareEnabled,
  });

  const compareLeftQuery = useHeatmapData({
    screenName,
    startTime,
    endTime,
    ...heatmapRequestFilters,
    enabled: compareSliceReady,
  });

  const compareRightQuery = useHeatmapData({
    screenName: compareScreenName.trim() || "HomeScreen",
    startTime,
    endTime,
    ...heatmapRequestFilters,
    enabled: compareSliceReady,
  });

  const singlePayload = heatmapQuery.data?.data;
  const singleErr = heatmapQuery.data?.error;
  const compareLeftPayload = compareLeftQuery.data?.data;
  const compareRightPayload = compareRightQuery.data?.data;
  const compareLeftErr = compareLeftQuery.data?.error;
  const compareRightErr = compareRightQuery.data?.error;

  const compareSharedMax = useMemo(
    () =>
      compareSharedWeightMax(compareLeftPayload, compareRightPayload, signal),
    [compareLeftPayload, compareRightPayload, signal],
  );

  const qualityMetrics = useMemo(
    () => getHeatmapQualityMetrics(singlePayload ?? null),
    [singlePayload],
  );

  const glowForSignal = useMemo(
    () => glowLayerForSignal(singlePayload ?? null, signal),
    [singlePayload, signal],
  );

  const rageForMarkers =
    singlePayload?.layers?.frustration_map?.rage?.map((r) => ({
      x: r.x,
      y: r.y,
      weight: r.weight,
    })) ?? [];

  const compareErrorMessage =
    compareLeftErr || compareRightErr || compareLeftQuery.isError || compareRightQuery.isError
      ? (compareLeftErr?.message ??
        compareRightErr?.message ??
        "Request failed")
      : null;

  if (compareEnabled) {
    return (
      <HeatmapComparePanel
        signal={signal}
        onSignalChange={setSignal}
        compareScreenName={compareScreenName}
        onCompareScreenNameChange={setCompareScreenName}
        onExitCompare={() => setCompareEnabled(false)}
        isLoading={
          compareLeftQuery.isLoading || compareRightQuery.isLoading
        }
        errorMessage={compareErrorMessage}
        compareLeftPayload={compareLeftPayload}
        compareRightPayload={compareRightPayload}
        compareSharedMax={compareSharedMax}
      />
    );
  }

  return (
    <HeatmapMainCard
      screenName={screenName}
      engagement={engagement}
      signal={signal}
      onSignalChange={setSignal}
      onCompareClick={() => setCompareEnabled(true)}
      isLoading={heatmapQuery.isLoading}
      errorMessage={
        singleErr || heatmapQuery.isError
          ? (singleErr?.message ?? "Request failed")
          : null
      }
      singlePayload={singlePayload}
      qualityMetrics={qualityMetrics}
      screenshotUrl={singlePayload?.metadata.screenshot_url ?? undefined}
      glowMap={glowForSignal}
      ragePoints={rageForMarkers}
      showFrustrationMarkers={signal === "rage"}
    />
  );
}
