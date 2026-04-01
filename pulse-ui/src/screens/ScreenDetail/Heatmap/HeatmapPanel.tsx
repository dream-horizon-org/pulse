import { useEffect, useMemo, useState } from "react";
import { useHeatmapData } from "../../../hooks/useHeatmapData";
import { useGetScreenNames } from "../../../hooks/useGetScreenNames";
import { useFilterStore } from "../../../stores/useFilterStore";
import { getHeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapVisualization } from "./HeatmapVisualization";
import { HeatmapComparePanel } from "./HeatmapComparePanel";
import { HeatmapVizFooter } from "./HeatmapVizFooter";
import { HeatmapMainCard } from "./HeatmapMainCard";
import { HeatmapFilterPanel } from "./HeatmapFilterPanel";
import { screenshotUrlsFromMetadata } from "./heatmapMetadataUtils";
import { useHeatmapBinBudget } from "./useHeatmapBinBudget";
import {
  compareSharedWeightMax,
  glowLayerForSignal,
  heatmapLayersIncludeInteractionMapKey,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import {
  defaultHeatmapLocalFilters,
  heatmapFiltersToRequestArgs,
  heatmapLocalFiltersMatchPage,
} from "./heatmapLocalFilters";
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
  const { filterValues } = useFilterStore();
  const [compareEnabled, setCompareEnabled] = useState(false);
  const [compareScreenName, setCompareScreenName] = useState("");
  const [signal, setSignal] = useState<HeatmapSignal>("tap");
  const [focusLens, setFocusLens] = useState<HeatmapFocusLens>("all");

  const pageBaseline = useMemo(
    () => defaultHeatmapLocalFilters(filterValues, startTime, endTime),
    [filterValues, startTime, endTime],
  );

  const [singleFilters, setSingleFilters] = useState<typeof pageBaseline | null>(
    null,
  );

  useEffect(() => {
    setSingleFilters((prev) => (prev == null ? pageBaseline : prev));
  }, [pageBaseline]);

  const effectiveSingle = singleFilters ?? pageBaseline;

  const [compareFiltersA, setCompareFiltersA] = useState<
    typeof pageBaseline | null
  >(null);
  const [compareFiltersB, setCompareFiltersB] = useState<
    typeof pageBaseline | null
  >(null);

  const effectiveCompareA = compareFiltersA ?? pageBaseline;
  const effectiveCompareB = compareFiltersB ?? pageBaseline;

  const singleMatchesPage = heatmapLocalFiltersMatchPage(
    effectiveSingle,
    filterValues,
    startTime,
    endTime,
  );

  const compareAMatchesPage = heatmapLocalFiltersMatchPage(
    effectiveCompareA,
    filterValues,
    startTime,
    endTime,
  );

  const compareBMatchesPage = heatmapLocalFiltersMatchPage(
    effectiveCompareB,
    filterValues,
    startTime,
    endTime,
  );

  const { screenNames: compareScreenNameOptions } = useGetScreenNames({
    startTime: effectiveCompareB.startTime,
    endTime: effectiveCompareB.endTime,
    enabled: compareEnabled,
  });

  const compareScreenSelectData = useMemo(() => {
    const rows = compareScreenNameOptions.map((n) => ({
      value: n,
      label: n,
    }));
    if (
      compareScreenName.trim() &&
      !rows.some((r) => r.value === compareScreenName)
    ) {
      return [
        { value: compareScreenName, label: compareScreenName },
        ...rows,
      ];
    }
    return rows;
  }, [compareScreenNameOptions, compareScreenName]);

  const compareSliceReady = compareEnabled && !!compareScreenName.trim();

  const openCompare = () => {
    const base = singleFilters ?? pageBaseline;
    setCompareFiltersA({ ...base });
    setCompareFiltersB({ ...base });
    setCompareEnabled(true);
  };

  useEffect(() => {
    if (!compareEnabled || compareScreenNameOptions.length === 0) return;
    if (!compareScreenName.trim()) {
      const alt =
        compareScreenNameOptions.find((s) => s !== screenName) ??
        compareScreenNameOptions[0];
      if (alt) setCompareScreenName(alt);
    }
  }, [
    compareEnabled,
    compareScreenName,
    compareScreenNameOptions,
    screenName,
  ]);

  const exitCompare = () => {
    setCompareEnabled(false);
    setCompareFiltersA(null);
    setCompareFiltersB(null);
  };

  const singleRequest = heatmapFiltersToRequestArgs(effectiveSingle);
  const compareARequest = heatmapFiltersToRequestArgs(effectiveCompareA);
  const compareBRequest = heatmapFiltersToRequestArgs(effectiveCompareB);

  const heatmapQuery = useHeatmapData({
    screenName,
    ...singleRequest,
    enabled: !compareEnabled,
  });

  const compareLeftQuery = useHeatmapData({
    screenName,
    ...compareARequest,
    enabled: compareSliceReady,
  });

  const compareRightQuery = useHeatmapData({
    screenName: compareScreenName.trim(),
    ...compareBRequest,
    enabled: compareSliceReady,
  });

  const singlePayload = heatmapQuery.data?.data;
  const singleErr = heatmapQuery.data?.error;
  const compareLeftPayload = compareLeftQuery.data?.data;
  const compareRightPayload = compareRightQuery.data?.data;
  const compareLeftErr = compareLeftQuery.data?.error;
  const compareRightErr = compareRightQuery.data?.error;

  const singleShowInteractionMap = useMemo(
    () =>
      singlePayload
        ? heatmapLayersIncludeInteractionMapKey(singlePayload.layers)
        : false,
    [singlePayload],
  );

  const compareShowInteractionMap = useMemo(
    () =>
      compareLeftPayload != null &&
      compareRightPayload != null &&
      heatmapLayersIncludeInteractionMapKey(compareLeftPayload.layers) &&
      heatmapLayersIncludeInteractionMapKey(compareRightPayload.layers),
    [compareLeftPayload, compareRightPayload],
  );

  useEffect(() => {
    const allowInteractionMap = compareEnabled
      ? compareShowInteractionMap
      : singleShowInteractionMap;
    if (!allowInteractionMap && focusLens === "key") {
      setFocusLens("all");
    }
  }, [
    compareEnabled,
    compareShowInteractionMap,
    singleShowInteractionMap,
    focusLens,
  ]);

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

  const compareErrorMessage =
    compareLeftErr ||
    compareRightErr ||
    compareLeftQuery.isError ||
    compareRightQuery.isError
      ? (compareLeftErr?.message ??
        compareRightErr?.message ??
        "Something went wrong. Try again.")
      : null;

  const heatmapErrorMessage =
    singleErr || heatmapQuery.isError
      ? (singleErr?.message ??
        "Something went wrong. Try again.")
      : null;

  if (compareEnabled) {
    return (
      <HeatmapComparePanel
        signal={signal}
        onSignalChange={setSignal}
        focusLens={focusLens}
        onFocusLensChange={setFocusLens}
        screenAName={screenName}
        compareScreenName={compareScreenName}
        onCompareScreenNameChange={setCompareScreenName}
        compareScreenOptions={compareScreenSelectData}
        filtersSlotA={
          <HeatmapFilterPanel
            variant="dataOnly"
            dataOnlyLayout="compareColumn"
            sectionLabel="Screen A"
            value={effectiveCompareA}
            onChange={(v) => setCompareFiltersA(v)}
            onResetToPage={() =>
              setCompareFiltersA(defaultHeatmapLocalFilters(
                filterValues,
                startTime,
                endTime,
              ))
            }
            matchesPage={compareAMatchesPage}
          />
        }
        filtersSlotB={
          <HeatmapFilterPanel
            variant="dataOnly"
            dataOnlyLayout="compareColumn"
            sectionLabel="Screen B"
            value={effectiveCompareB}
            onChange={(v) => setCompareFiltersB(v)}
            onResetToPage={() =>
              setCompareFiltersB(defaultHeatmapLocalFilters(
                filterValues,
                startTime,
                endTime,
              ))
            }
            matchesPage={compareBMatchesPage}
          />
        }
        onExitCompare={exitCompare}
        isLoading={
          compareLeftQuery.isLoading || compareRightQuery.isLoading
        }
        errorMessage={compareErrorMessage}
        compareLeftPayload={compareLeftPayload}
        compareRightPayload={compareRightPayload}
        compareLeftQualityMetrics={compareLeftQualityMetrics}
        compareRightQualityMetrics={compareRightQualityMetrics}
        compareSharedMax={compareSharedMax}
        showInteractionMapOption={compareShowInteractionMap}
      />
    );
  }

  return (
    <div className={classes.root}>
      <HeatmapMainCard
        engagement={engagement}
        signal={signal}
        focusLens={focusLens}
        isLoading={heatmapQuery.isLoading}
        errorMessage={heatmapErrorMessage}
        singlePayload={singlePayload}
        qualityMetrics={qualityMetrics}
        mapToolbar={
          <HeatmapFilterPanel
            variant="full"
            value={effectiveSingle}
            onChange={(v) => setSingleFilters(v)}
            onResetToPage={() =>
              setSingleFilters(
                defaultHeatmapLocalFilters(
                  filterValues,
                  startTime,
                  endTime,
                ),
              )
            }
            matchesPage={singleMatchesPage}
            signal={signal}
            onSignalChange={setSignal}
            focusLens={focusLens}
            onFocusLensChange={setFocusLens}
            showInteractionMapOption={singleShowInteractionMap}
            toolbarEnd={
              <button
                type="button"
                className={classes.compareCta}
                onClick={openCompare}
              >
                Compare screens
              </button>
            }
          />
        }
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
                densityBinTooltip={{ payload: singlePayload, signal }}
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
    </div>
  );
}
