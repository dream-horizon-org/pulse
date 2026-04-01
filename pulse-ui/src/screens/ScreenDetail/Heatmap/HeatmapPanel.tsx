import { useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { useHeatmapData } from "../../../hooks/useHeatmapData";
import { useFilterStore } from "../../../stores/useFilterStore";
import { ROUTES } from "../../../constants";
import { getHeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapVisualization } from "./HeatmapVisualization";
import { HeatmapComparePanel } from "./HeatmapComparePanel";
import { HeatmapDrillDown, HeatmapEngagementCards } from "./HeatmapPanelFooter";
import { HeatmapVizFooter } from "./HeatmapVizFooter";
import { HeatmapMainCard } from "./HeatmapMainCard";
import { screenshotUrlsFromMetadata } from "./heatmapMetadataUtils";
import { useHeatmapBinBudget } from "./useHeatmapBinBudget";
import {
  compareSharedWeightMax,
  glowLayerForSignal,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import type { HeatmapPanelProps } from "./heatmapPanel.types";
import classes from "./HeatmapPanel.module.css";

export type { HeatmapPanelProps } from "./heatmapPanel.types";

/**
 * Heatmap tab — Summary + Map cards (EngagementGraph styling), demo-aligned split
 * and aggregates panel; retains heatmap.js, focus lens, carousel, and bin budget.
 */
export function HeatmapPanel({
  screenName,
  startTime,
  endTime,
  engagement,
}: HeatmapPanelProps) {
  const { projectId } = useParams<{ projectId: string }>();
  const { filterValues } = useFilterStore();
  const [compareEnabled, setCompareEnabled] = useState(false);
  const [compareScreenName, setCompareScreenName] = useState("HomeScreen");
  const [signal, setSignal] = useState<HeatmapSignal>("tap");
  const [focusLens, setFocusLens] = useState<HeatmapFocusLens>("all");

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

  const compareLeftQualityMetrics = useMemo(
    () => getHeatmapQualityMetrics(compareLeftPayload ?? null),
    [compareLeftPayload],
  );

  const compareRightQualityMetrics = useMemo(
    () => getHeatmapQualityMetrics(compareRightPayload ?? null),
    [compareRightPayload],
  );

  const qualityMetrics = useMemo(
    () => getHeatmapQualityMetrics(singlePayload ?? null),
    [singlePayload],
  );

  const glowForSignal = useMemo(
    () => glowLayerForSignal(singlePayload ?? null, signal),
    [singlePayload, signal],
  );

  const binBudget = useHeatmapBinBudget(glowForSignal);

  const rageForMarkers =
    singlePayload?.layers?.frustration_map?.rage?.map((r) => ({
      x: r.x,
      y: r.y,
      weight: r.weight,
    })) ?? [];

  const userEngagementPath = projectId
    ? ROUTES.PROJECT_USER_ENGAGEMENT.path.replace(":projectId", projectId)
    : "#";
  const appVitalsPath = projectId
    ? ROUTES.PROJECT_APP_VITALS.path.replace(":projectId", projectId)
    : "#";

  const compareErrorMessage =
    compareLeftErr || compareRightErr || compareLeftQuery.isError || compareRightQuery.isError
      ? (compareLeftErr?.message ??
        compareRightErr?.message ??
        "Request failed")
      : null;

  const heatmapErrorMessage =
    singleErr || heatmapQuery.isError
      ? (singleErr?.message ?? "Request failed")
      : null;

  if (compareEnabled) {
    return (
      <HeatmapComparePanel
        signal={signal}
        onSignalChange={setSignal}
        focusLens={focusLens}
        onFocusLensChange={setFocusLens}
        compareScreenName={compareScreenName}
        onCompareScreenNameChange={setCompareScreenName}
        onExitCompare={() => setCompareEnabled(false)}
        isLoading={
          compareLeftQuery.isLoading || compareRightQuery.isLoading
        }
        errorMessage={compareErrorMessage}
        compareLeftPayload={compareLeftPayload}
        compareRightPayload={compareRightPayload}
        compareLeftQualityMetrics={compareLeftQualityMetrics}
        compareRightQualityMetrics={compareRightQualityMetrics}
        compareSharedMax={compareSharedMax}
      />
    );
  }

  return (
    <div className={classes.root}>
      <HeatmapMainCard
        engagement={engagement}
        signal={signal}
        onSignalChange={setSignal}
        onCompareClick={() => setCompareEnabled(true)}
        focusLens={focusLens}
        onFocusLensChange={setFocusLens}
        isLoading={heatmapQuery.isLoading}
        errorMessage={heatmapErrorMessage}
        singlePayload={singlePayload}
        qualityMetrics={qualityMetrics}
        mapColumn={
          singlePayload ? (
            <div className={classes.heatVizEmbedded}>
              <HeatmapVisualization
                embedded
                signal={signal}
                screenshotUrls={screenshotUrlsFromMetadata(singlePayload.metadata)}
                glowMap={glowForSignal}
                binBudget={binBudget}
                showDensityFooter={false}
                focusLens={focusLens}
                interactionRegions={
                  singlePayload.layers.interaction_map?.regions ?? []
                }
                showFrustrationMarkers={signal === "rage"}
                ragePoints={rageForMarkers}
              />
              {focusLens === "all" && (
                <div className={classes.embeddedBinBudget}>
                  <HeatmapVizFooter
                    glowMapLength={glowForSignal.length}
                    displayCount={binBudget.displayGlow.length}
                    binBudgetMax={binBudget.binBudgetMax}
                    effectiveBudget={binBudget.binBudget}
                    onBudgetChange={binBudget.setBinBudget}
                  />
                </div>
              )}
            </div>
          ) : null
        }
      />

      <HeatmapDrillDown
        userEngagementPath={userEngagementPath}
        appVitalsPath={appVitalsPath}
      />

      <HeatmapEngagementCards engagement={engagement} />
    </div>
  );
}
