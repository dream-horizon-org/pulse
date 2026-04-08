import { useEffect, useMemo, useRef, useState } from "react";
import { useHeatmapData } from "../../../hooks/useHeatmapData";
import { useGetScreenNames } from "../../../hooks/useGetScreenNames";
import { useFilterStore } from "../../../stores/useFilterStore";
import { getHeatmapQualityMetrics } from "./heatmapQuality";
import { isHeatmapDataEmpty } from "./heatmapEmptyState";
import {
  notifyHeatmapTechnicalDetail,
  shouldToastHeatmapErrorDetail,
} from "./heatmapFetchErrors";
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
  heatmapShowsKeyActionsLens,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import {
  defaultHeatmapLocalFilters,
  heatmapFiltersToRequestArgs,
  heatmapLocalFiltersMatchPage,
} from "./heatmapLocalFilters";
import {
  HEATMAP_COPY_COMPARE_SCREENS,
  HEATMAP_COPY_SECTION_SCREEN_A,
  HEATMAP_COPY_SECTION_SCREEN_B,
} from "./heatmapCopy";
import type { HeatmapPanelProps } from "./heatmap.ui.types";
import {
  isHeatmapMockServerEnabled,
  mockProfileToApiScreenName,
  type HeatmapMockProfile,
} from "./heatmapMockDev";
import { HeatmapMockScenarioToolbar } from "./HeatmapMockScenarioToolbar";
import classes from "./HeatmapPanel.module.css";

export type { HeatmapPanelProps } from "./heatmap.ui.types";

/**
 * Heatmap tab — Summary + Map cards (EngagementGraph styling), demo-aligned split
 * and aggregates panel; retains heatmap.js, focus lens, carousel, and bin budget.
 *
 * Compare mode issues two independent `useHeatmapData` queries (screen A vs B) so each
 * column can use different filters; a single `POST …/heatmap/compare` exists on the API
 * for mocks/types but is optional for the UI until we consolidate round-trips.
 */
export function HeatmapPanel({
  screenName,
  startTime,
  endTime,
  engagement,
}: HeatmapPanelProps) {
  const { filterValues } = useFilterStore();
  const mockServer = isHeatmapMockServerEnabled();
  const [mockProfileMain, setMockProfileMain] =
    useState<HeatmapMockProfile>("live");
  const [mockProfileCompareB, setMockProfileCompareB] =
    useState<HeatmapMockProfile>("live");
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

  const effectiveApiScreenMain = useMemo(
    () =>
      mockServer
        ? mockProfileToApiScreenName(mockProfileMain, screenName)
        : screenName,
    [mockServer, mockProfileMain, screenName],
  );

  const effectiveApiScreenCompareB = useMemo(() => {
    const b = compareScreenName.trim();
    if (!mockServer) return b;
    return mockProfileToApiScreenName(mockProfileCompareB, b);
  }, [mockServer, mockProfileCompareB, compareScreenName]);

  const mockScenarioToolbar = mockServer ? (
    <HeatmapMockScenarioToolbar
      compareMode={compareEnabled}
      profileMain={mockProfileMain}
      onProfileMainChange={setMockProfileMain}
      profileCompareB={mockProfileCompareB}
      onProfileCompareBChange={setMockProfileCompareB}
    />
  ) : null;

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
    screenName: effectiveApiScreenMain,
    ...singleRequest,
    enabled: !compareEnabled,
  });

  const compareLeftQuery = useHeatmapData({
    screenName: effectiveApiScreenMain,
    ...compareARequest,
    enabled: compareSliceReady,
  });

  const compareRightQuery = useHeatmapData({
    screenName: effectiveApiScreenCompareB,
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
      singlePayload ? heatmapShowsKeyActionsLens(singlePayload.layers) : false,
    [singlePayload],
  );

  const compareShowInteractionMap = useMemo(
    () =>
      compareLeftPayload != null &&
      compareRightPayload != null &&
      heatmapShowsKeyActionsLens(compareLeftPayload.layers) &&
      heatmapShowsKeyActionsLens(compareRightPayload.layers),
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

  const binBudget = useHeatmapBinBudget(glowForSignal, mockServer);

  const rageForMarkers =
    singlePayload?.layers?.frustration_map?.rage?.map((r) => ({
      x: r.x,
      y: r.y,
      weight: r.weight,
    })) ?? [];

  const compareLeftFetchFailed =
    !!compareLeftErr || compareLeftQuery.isError;

  const compareRightFetchFailed =
    !!compareRightErr || compareRightQuery.isError;

  const heatmapFetchFailed = !!singleErr || heatmapQuery.isError;

  const heatmapErrorToastKeyRef = useRef<string | null>(null);

  useEffect(() => {
    const failedSingle = !compareEnabled && heatmapFetchFailed;
    const failedCompare =
      compareEnabled && (compareLeftFetchFailed || compareRightFetchFailed);
    if (!failedSingle && !failedCompare) {
      heatmapErrorToastKeyRef.current = null;
      return;
    }

    let detail = "";
    if (!compareEnabled && heatmapFetchFailed) {
      detail =
        singleErr?.message?.trim() ||
        singleErr?.cause?.trim() ||
        (heatmapQuery.error instanceof Error
          ? heatmapQuery.error.message
          : "") ||
        "";
    } else if (compareEnabled && failedCompare) {
      const parts: string[] = [];
      if (compareLeftFetchFailed) {
        const t =
          compareLeftErr?.message?.trim() ||
          compareLeftErr?.cause?.trim() ||
          (compareLeftQuery.error instanceof Error
            ? compareLeftQuery.error.message
            : "");
        if (t) parts.push(`A: ${t}`);
      }
      if (compareRightFetchFailed) {
        const t =
          compareRightErr?.message?.trim() ||
          compareRightErr?.cause?.trim() ||
          (compareRightQuery.error instanceof Error
            ? compareRightQuery.error.message
            : "");
        if (t) parts.push(`B: ${t}`);
      }
      detail = parts.join("\n");
    }

    if (!detail || !shouldToastHeatmapErrorDetail(detail)) return;

    const key = [
      compareEnabled ? "c" : "s",
      compareLeftFetchFailed ? "L" : "",
      compareRightFetchFailed ? "R" : "",
      effectiveApiScreenMain,
      effectiveApiScreenCompareB,
      detail,
    ].join("|");

    if (heatmapErrorToastKeyRef.current === key) return;
    heatmapErrorToastKeyRef.current = key;
    notifyHeatmapTechnicalDetail(detail);
  }, [
    compareEnabled,
    heatmapFetchFailed,
    compareLeftFetchFailed,
    compareRightFetchFailed,
    singleErr,
    compareLeftErr,
    compareRightErr,
    heatmapQuery.error,
    compareLeftQuery.error,
    compareRightQuery.error,
    effectiveApiScreenMain,
    effectiveApiScreenCompareB,
  ]);

  if (compareEnabled) {
    return (
      <>
        {mockScenarioToolbar}
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
              sectionLabel={HEATMAP_COPY_SECTION_SCREEN_A}
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
              sectionLabel={HEATMAP_COPY_SECTION_SCREEN_B}
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
          compareLeftLoading={compareLeftQuery.isLoading}
          compareRightLoading={compareRightQuery.isLoading}
          compareLeftFetchFailed={compareLeftFetchFailed}
          compareRightFetchFailed={compareRightFetchFailed}
          onRetryCompareLeft={() => {
            void compareLeftQuery.refetch();
          }}
          onRetryCompareRight={() => {
            void compareRightQuery.refetch();
          }}
          compareLeftRetrying={
            compareLeftQuery.isFetching && !compareLeftQuery.isPending
          }
          compareRightRetrying={
            compareRightQuery.isFetching && !compareRightQuery.isPending
          }
          compareLeftPayload={compareLeftPayload}
          compareRightPayload={compareRightPayload}
          compareLeftQualityMetrics={compareLeftQualityMetrics}
          compareRightQualityMetrics={compareRightQualityMetrics}
          compareSharedMax={compareSharedMax}
          showInteractionMapOption={compareShowInteractionMap}
          compareLeftBreakpoint={effectiveCompareA.breakpoint}
          compareRightBreakpoint={effectiveCompareB.breakpoint}
        />
      </>
    );
  }

  return (
    <div className={classes.root}>
      {mockScenarioToolbar}
      <HeatmapMainCard
        engagement={engagement}
        signal={signal}
        focusLens={focusLens}
        isLoading={heatmapQuery.isLoading}
        heatmapFetchError={heatmapFetchFailed}
        heatmapRetryLoading={
          heatmapQuery.isFetching && !heatmapQuery.isPending
        }
        onHeatmapRetry={() => {
          void heatmapQuery.refetch();
        }}
        singlePayload={singlePayload}
        contextScreenName={screenName}
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
                {HEATMAP_COPY_COMPARE_SCREENS}
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
                breakpoint={effectiveSingle.breakpoint}
                interactionRegions={
                  singlePayload.layers.interaction_map?.regions ?? []
                }
                showFrustrationMarkers={signal === "rage"}
                ragePoints={rageForMarkers}
                densityBinTooltip={
                  isHeatmapDataEmpty(singlePayload)
                    ? undefined
                    : { payload: singlePayload, signal }
                }
              />
              {mockServer && !isHeatmapDataEmpty(singlePayload) &&
                focusLens === "all" &&
                 (
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
