import { type ReactNode, useMemo } from "react";
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
import { useResolvedHeatmapScreenshots } from "./useResolvedHeatmapScreenshots";
import {
  glowLayerForSignal,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import { isHeatmapDataEmpty } from "./heatmapEmptyState";
import { HeatmapVisualization } from "./HeatmapVisualization";
import { isHeatmapMockServerEnabled } from "./heatmapMockDev";
import { useHeatmapBinBudget } from "./useHeatmapBinBudget";
import { HeatmapAggregatesPanel } from "./HeatmapAggregatesPanel";
import type { HeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapMapViewControls } from "./HeatmapMapViewControls";
import { HeatmapMapPlaceholder } from "./HeatmapMapPlaceholder";
import { HeatmapDataEmptyAside } from "./HeatmapDataEmptyAside";
import { HeatmapInvalidTimeRangeAside } from "./HeatmapInvalidTimeRangeAside";
import { HeatmapFetchErrorPanel } from "./HeatmapFetchErrorPanel";
import {
  HEATMAP_COPY_COMPARE_MODE_TITLE,
  HEATMAP_COPY_COMPARE_TO_PLACEHOLDER,
  HEATMAP_COPY_COMPARE_TO_SCREEN,
  HEATMAP_COPY_CURRENT_SCREEN,
  HEATMAP_COPY_EXIT_COMPARE,
  HEATMAP_COPY_LOADING_COMPARISON,
  HEATMAP_COPY_LOADING_METRICS,
  HEATMAP_COPY_METRICS_BLOCKED_AFTER,
  HEATMAP_COPY_METRICS_BLOCKED_BEFORE,
  HEATMAP_COPY_RETRY,
  HEATMAP_COPY_SCREEN_B_FALLBACK,
} from "./heatmapCopy";
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
  compareLeftBreakpoint?: string;
  compareRightBreakpoint?: string;
  /** Heatmap column A: From/To missing or invalid in its time filter. */
  compareLeftTimeInvalid?: boolean;
  compareRightTimeInvalid?: boolean;
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
  compareLeftBreakpoint = "",
  compareRightBreakpoint = "",
  compareLeftTimeInvalid = false,
  compareRightTimeInvalid = false,
}: HeatmapComparePanelProps) {
  const anyLoading = compareLeftLoading || compareRightLoading;

  return (
    <Stack gap="md" className={classes.root}>
      <Box className={classes.filterBar}>
        <Stack gap="md">
          <Text fw={700} size="md">
            {HEATMAP_COPY_COMPARE_MODE_TITLE}
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
              {HEATMAP_COPY_EXIT_COMPARE}
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
          <Text size="sm">{HEATMAP_COPY_LOADING_COMPARISON}</Text>
        </Group>
      )}

      <div className={classes.compareGrid}>
        <CompareMapColumn
          headerSlot={
            <Select
              label={HEATMAP_COPY_CURRENT_SCREEN}
              placeholder="—"
              size="sm"
              data={[{ value: screenAName, label: screenAName }]}
              value={screenAName}
              disabled
            />
          }
          timeInvalid={compareLeftTimeInvalid}
          loading={compareLeftLoading}
          fetchFailed={compareLeftFetchFailed}
          onRetry={onRetryCompareLeft}
          retryLoading={compareLeftRetrying}
          payload={compareLeftPayload}
          screenLabel={screenAName}
          signal={signal}
          focusLens={focusLens}
          sharedWeightMax={compareSharedMax}
          breakpoint={compareLeftBreakpoint}
        />
        <CompareMapColumn
          headerSlot={
            <Select
              label={HEATMAP_COPY_COMPARE_TO_SCREEN}
              placeholder={HEATMAP_COPY_COMPARE_TO_PLACEHOLDER}
              size="sm"
              searchable
              data={compareScreenOptions}
              value={compareScreenName}
              onChange={(v) => onCompareScreenNameChange(v ?? "")}
            />
          }
          timeInvalid={compareRightTimeInvalid}
          loading={compareRightLoading}
          fetchFailed={compareRightFetchFailed}
          onRetry={onRetryCompareRight}
          retryLoading={compareRightRetrying}
          payload={compareRightPayload}
          screenLabel={compareScreenName.trim() || HEATMAP_COPY_SCREEN_B_FALLBACK}
          signal={signal}
          focusLens={focusLens}
          sharedWeightMax={compareSharedMax}
          breakpoint={compareRightBreakpoint}
        />
      </div>

      <div className={classes.compareAggregatesGrid}>
        <CompareAggregatesCell
          title={screenAName}
          timeInvalid={compareLeftTimeInvalid}
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
            (compareScreenName.trim() || HEATMAP_COPY_SCREEN_B_FALLBACK)
          }
          timeInvalid={compareRightTimeInvalid}
          loading={compareRightLoading}
          fetchFailed={compareRightFetchFailed}
          payload={compareRightPayload}
          contextScreenName={
            compareScreenName.trim() || HEATMAP_COPY_SCREEN_B_FALLBACK
          }
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
  timeInvalid = false,
  loading,
  fetchFailed,
  payload,
  contextScreenName,
  signal,
  qualityMetrics,
  focusLens,
}: {
  title: string;
  timeInvalid?: boolean;
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
      {timeInvalid && (
        <Box py="sm">
          <HeatmapInvalidTimeRangeAside />
        </Box>
      )}
      {!timeInvalid && fetchFailed && (
        <Text size="sm" c="dimmed" py="md" lh={1.5}>
          {HEATMAP_COPY_METRICS_BLOCKED_BEFORE}
          <Text span fw={600} c="dimmed">
            {HEATMAP_COPY_RETRY}
          </Text>
          {HEATMAP_COPY_METRICS_BLOCKED_AFTER}
        </Text>
      )}
      {!timeInvalid && !fetchFailed && loading && !payload && (
        <Group gap="sm" py="md" justify="center" w="100%" wrap="nowrap">
          <Loader size="sm" color="teal" />
          <Text size="sm" c="dimmed">
            {HEATMAP_COPY_LOADING_METRICS}
          </Text>
        </Group>
      )}
      {!timeInvalid && !fetchFailed && payload && !isHeatmapDataEmpty(payload) && (
        <HeatmapAggregatesPanel
          payload={payload}
          signal={signal}
          qualityMetrics={qualityMetrics}
          focusLens={focusLens}
        />
      )}
      {!timeInvalid && !fetchFailed && payload && isHeatmapDataEmpty(payload) && (
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
  timeInvalid = false,
  loading,
  fetchFailed,
  onRetry,
  retryLoading,
  payload,
  screenLabel,
  signal,
  focusLens,
  sharedWeightMax,
  breakpoint,
}: {
  headerSlot: ReactNode;
  timeInvalid?: boolean;
  loading: boolean;
  fetchFailed: boolean;
  onRetry?: () => void;
  retryLoading?: boolean;
  payload: HeatmapDataResponse | null | undefined;
  screenLabel: string;
  signal: HeatmapSignal;
  focusLens: HeatmapFocusLens;
  sharedWeightMax: number;
  breakpoint?: string;
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
        {!fetchFailed && timeInvalid && (
          <HeatmapInvalidTimeRangeAside />
        )}
        {!fetchFailed && !timeInvalid && loading && !payload && (
          <HeatmapMapPlaceholder />
        )}
        {!fetchFailed && !timeInvalid && !loading && payload && (
          <CompareColumnVisualization
            data={payload}
            signal={signal}
            focusLens={focusLens}
            sharedWeightMax={sharedWeightMax}
            showBinTooltip={!isHeatmapDataEmpty(payload)}
            breakpoint={breakpoint}
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
  breakpoint = "",
}: {
  data: HeatmapDataResponse;
  signal: HeatmapSignal;
  focusLens: HeatmapFocusLens;
  sharedWeightMax: number;
  showBinTooltip: boolean;
  breakpoint?: string;
}) {
  const glow = glowLayerForSignal(data, signal);
  const mockBinControls = isHeatmapMockServerEnabled();
  const binBudget = useHeatmapBinBudget(glow, mockBinControls);
  const rawScreenshotUrls = useMemo(
    () => screenshotUrlsFromMetadata(data.metadata),
    [data],
  );
  const {
    displayUrls: screenshotDisplayUrls,
    captureAppVersions: screenshotCaptureAppVersions,
    captureBreakpoints: screenshotCaptureBreakpoints,
    loading: screenshotsLoading,
    sourceKey: screenshotSourceKey,
  } = useResolvedHeatmapScreenshots(rawScreenshotUrls);

  return (
    <HeatmapVisualization
      signal={signal}
      screenshotUrls={screenshotDisplayUrls}
      screenshotCarouselCount={rawScreenshotUrls.length}
      screenshotSourceKey={screenshotSourceKey}
      screenshotsLoading={screenshotsLoading}
      screenshotCaptureAppVersions={screenshotCaptureAppVersions}
      screenshotCaptureBreakpoints={screenshotCaptureBreakpoints}
      glowMap={glow}
      binBudget={binBudget}
      focusLens={focusLens}
      breakpoint={breakpoint}
      interactionRegions={data.layers.interaction_map?.regions ?? []}
      sharedWeightMax={sharedWeightMax}
      showDensityFooter={focusLens === "all" && mockBinControls}
      densityBinTooltip={
        showBinTooltip ? { payload: data, signal } : undefined
      }
    />
  );
}
