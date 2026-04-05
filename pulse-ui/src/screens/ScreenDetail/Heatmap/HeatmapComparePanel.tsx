import { type ReactNode } from "react";
import {
  Box,
  Divider,
  Group,
  Loader,
  Select,
  Stack,
  Text,
} from "@mantine/core";
import type { HeatmapDataResponse } from "./heatmap.types";
import { screenshotUrlsFromMetadata } from "./heatmapMetadataUtils";
import {
  glowLayerForSignal,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import { isHeatmapDataEmpty } from "./heatmapEmptyState";
import { HeatmapVisualization } from "./HeatmapVisualization";
import { useHeatmapBinBudget } from "./useHeatmapBinBudget";
import { HeatmapAggregatesPanel } from "./HeatmapAggregatesPanel";
import type { HeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapMapViewControls } from "./HeatmapMapViewControls";
import { HeatmapMapPlaceholder } from "./HeatmapMapPlaceholder";
import { HeatmapDataEmptyAside } from "./HeatmapDataEmptyAside";
import { HeatmapFetchErrorPanel } from "./HeatmapFetchErrorPanel";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapComparePanelProps {
  signal: HeatmapSignal;
  onSignalChange: (s: HeatmapSignal) => void;
  focusLens: HeatmapFocusLens;
  onFocusLensChange: (l: HeatmapFocusLens) => void;
  screenAName: string;
  compareScreenName: string;
  onCompareScreenNameChange: (v: string) => void;
  compareScreenOptions: { value: string; label: string }[];
  filtersSlotA: ReactNode;
  filtersSlotB: ReactNode;
  onExitCompare: () => void;
  /** True while either side is fetching (initial or refetch). */
  compareLeftLoading: boolean;
  compareRightLoading: boolean;
  compareLeftFetchFailed: boolean;
  compareRightFetchFailed: boolean;
  onRetryCompareLeft?: () => void;
  onRetryCompareRight?: () => void;
  compareLeftRetrying?: boolean;
  compareRightRetrying?: boolean;
  compareLeftPayload: HeatmapDataResponse | null | undefined;
  compareRightPayload: HeatmapDataResponse | null | undefined;
  compareLeftQualityMetrics: HeatmapQualityMetrics;
  compareRightQualityMetrics: HeatmapQualityMetrics;
  compareSharedMax: number;
  showInteractionMapOption?: boolean;
}

export function HeatmapComparePanel({
  signal,
  onSignalChange,
  focusLens,
  onFocusLensChange,
  screenAName,
  compareScreenName,
  onCompareScreenNameChange,
  compareScreenOptions,
  filtersSlotA,
  filtersSlotB,
  onExitCompare,
  compareLeftLoading,
  compareRightLoading,
  compareLeftFetchFailed,
  compareRightFetchFailed,
  onRetryCompareLeft,
  onRetryCompareRight,
  compareLeftRetrying = false,
  compareRightRetrying = false,
  compareLeftPayload,
  compareRightPayload,
  compareLeftQualityMetrics,
  compareRightQualityMetrics,
  compareSharedMax,
  showInteractionMapOption = true,
}: HeatmapComparePanelProps) {
  const anyLoading = compareLeftLoading || compareRightLoading;

  return (
    <Stack gap="md" className={classes.root}>
      <Box className={classes.filterBar}>
        <Stack gap="md">
          <Text fw={700} size="md">
            Compare screens
          </Text>
          <Group justify="space-between" align="center" wrap="wrap" w="100%" gap="md">
            <HeatmapMapViewControls
              signal={signal}
              onSignalChange={onSignalChange}
              focusLens={focusLens}
              onFocusLensChange={onFocusLensChange}
              showInteractionMapOption={showInteractionMapOption}
            />
            <button type="button" className={classes.compareCta} onClick={onExitCompare}>
              Exit compare
            </button>
          </Group>
          <Divider
            w="100%"
            color="var(--mantine-color-gray-3)"
            style={{ opacity: 0.85 }}
          />
          <div className={classes.compareFilterColumns}>
            {filtersSlotA}
            {filtersSlotB}
          </div>
        </Stack>
      </Box>

      {anyLoading && (
        <Group justify="center" w="100%" py="sm" gap="sm" wrap="nowrap">
          <Loader size="sm" color="teal" />
          <Text size="sm">Loading comparison…</Text>
        </Group>
      )}

      <div className={classes.compareGrid}>
        <CompareMapColumn
          headerSlot={
            <Select
              label="Current screen"
              placeholder="—"
              size="sm"
              data={[{ value: screenAName, label: screenAName }]}
              value={screenAName}
              disabled
            />
          }
          loading={compareLeftLoading}
          fetchFailed={compareLeftFetchFailed}
          onRetry={onRetryCompareLeft}
          retryLoading={compareLeftRetrying}
          payload={compareLeftPayload}
          screenLabel={screenAName}
          signal={signal}
          focusLens={focusLens}
          sharedWeightMax={compareSharedMax}
        />
        <CompareMapColumn
          headerSlot={
            <Select
              label="Compare to screen"
              placeholder="Choose a screen"
              size="sm"
              searchable
              data={compareScreenOptions}
              value={compareScreenName}
              onChange={(v) => onCompareScreenNameChange(v ?? "")}
            />
          }
          loading={compareRightLoading}
          fetchFailed={compareRightFetchFailed}
          onRetry={onRetryCompareRight}
          retryLoading={compareRightRetrying}
          payload={compareRightPayload}
          screenLabel={compareScreenName.trim() || "Screen B"}
          signal={signal}
          focusLens={focusLens}
          sharedWeightMax={compareSharedMax}
        />
      </div>

      <div className={classes.compareAggregatesGrid}>
        <CompareAggregatesCell
          title={screenAName}
          loading={compareLeftLoading}
          fetchFailed={compareLeftFetchFailed}
          payload={compareLeftPayload}
          contextScreenName={screenAName}
          signal={signal}
          qualityMetrics={compareLeftQualityMetrics}
          focusLens={focusLens}
        />
        <CompareAggregatesCell
          title={
            compareRightPayload?.metadata.screenName ??
            (compareScreenName.trim() || "Screen B")
          }
          loading={compareRightLoading}
          fetchFailed={compareRightFetchFailed}
          payload={compareRightPayload}
          contextScreenName={compareScreenName.trim() || "Screen B"}
          signal={signal}
          qualityMetrics={compareRightQualityMetrics}
          focusLens={focusLens}
        />
      </div>
    </Stack>
  );
}

function CompareAggregatesCell({
  title,
  loading,
  fetchFailed,
  payload,
  contextScreenName,
  signal,
  qualityMetrics,
  focusLens,
}: {
  title: string;
  loading: boolean;
  fetchFailed: boolean;
  payload: HeatmapDataResponse | null | undefined;
  contextScreenName: string;
  signal: HeatmapSignal;
  qualityMetrics: HeatmapQualityMetrics;
  focusLens: HeatmapFocusLens;
}) {
  return (
    <Box className={classes.compareAggregatesCell}>
      <Text fw={700} mb="sm" size="sm">
        {title}
      </Text>
      {fetchFailed && (
        <Text size="sm" c="dimmed" py="md" lh={1.5}>
          Metrics load with the heatmap preview. Use{" "}
          <Text span fw={600} c="dimmed">
            Retry
          </Text>{" "}
          in the column above.
        </Text>
      )}
      {!fetchFailed && loading && !payload && (
        <Group gap="sm" py="md" justify="center" w="100%" wrap="nowrap">
          <Loader size="sm" color="teal" />
          <Text size="sm" c="dimmed">
            Loading metrics…
          </Text>
        </Group>
      )}
      {!fetchFailed && payload && !isHeatmapDataEmpty(payload) && (
        <HeatmapAggregatesPanel
          payload={payload}
          signal={signal}
          qualityMetrics={qualityMetrics}
          focusLens={focusLens}
        />
      )}
      {!fetchFailed && payload && isHeatmapDataEmpty(payload) && (
        <HeatmapDataEmptyAside
          screenName={payload.metadata.screenName}
          contextScreenName={contextScreenName}
        />
      )}
    </Box>
  );
}

function CompareMapColumn({
  headerSlot,
  loading,
  fetchFailed,
  onRetry,
  retryLoading,
  payload,
  screenLabel,
  signal,
  focusLens,
  sharedWeightMax,
}: {
  headerSlot: ReactNode;
  loading: boolean;
  fetchFailed: boolean;
  onRetry?: () => void;
  retryLoading?: boolean;
  payload: HeatmapDataResponse | null | undefined;
  screenLabel: string;
  signal: HeatmapSignal;
  focusLens: HeatmapFocusLens;
  sharedWeightMax: number;
}) {
  return (
    <Box>
      {headerSlot}
      <Box mt="md">
        {fetchFailed && (
          <HeatmapFetchErrorPanel
            compact
            onRetry={onRetry}
            retryLoading={retryLoading}
          />
        )}
        {!fetchFailed && loading && !payload && (
          <HeatmapMapPlaceholder variant="loading" />
        )}
        {!fetchFailed && !loading && payload && (
          <CompareColumnVisualization
            data={payload}
            signal={signal}
            focusLens={focusLens}
            sharedWeightMax={sharedWeightMax}
            showBinTooltip={!isHeatmapDataEmpty(payload)}
          />
        )}
      </Box>
    </Box>
  );
}

function CompareColumnVisualization({
  data,
  signal,
  focusLens,
  sharedWeightMax,
  showBinTooltip,
}: {
  data: HeatmapDataResponse;
  signal: HeatmapSignal;
  focusLens: HeatmapFocusLens;
  sharedWeightMax: number;
  showBinTooltip: boolean;
}) {
  const glow = glowLayerForSignal(data, signal);
  const map = glow.length ? glow : data.layers.glow_map;
  const binBudget = useHeatmapBinBudget(map);
  const rageForMarkers =
    data.layers?.frustration_map?.rage?.map((r) => ({
      x: r.x,
      y: r.y,
      weight: r.weight,
    })) ?? [];

  return (
    <HeatmapVisualization
      signal={signal}
      screenshotUrls={screenshotUrlsFromMetadata(data.metadata)}
      glowMap={map}
      binBudget={binBudget}
      focusLens={focusLens}
      interactionRegions={data.layers.interaction_map?.regions ?? []}
      sharedWeightMax={sharedWeightMax}
      showDensityFooter={focusLens === "all"}
      showFrustrationMarkers={signal === "rage"}
      ragePoints={rageForMarkers}
      densityBinTooltip={
        showBinTooltip ? { payload: data, signal } : undefined
      }
    />
  );
}
