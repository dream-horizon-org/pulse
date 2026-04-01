import {
  Box,
  Stack,
  Group,
  Text,
  Loader,
  Alert,
  TextInput,
} from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import type { HeatmapDataResponse } from "./heatmap.types";
import { screenshotUrlsFromMetadata } from "./heatmapMetadataUtils";
import {
  HEATMAP_SIGNALS,
  glowLayerForSignal,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import { HeatmapVisualization } from "./HeatmapVisualization";
import { useHeatmapBinBudget } from "./useHeatmapBinBudget";
import { HeatmapAggregatesPanel } from "./HeatmapAggregatesPanel";
import type { HeatmapQualityMetrics } from "./heatmapQuality";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapComparePanelProps {
  signal: HeatmapSignal;
  onSignalChange: (s: HeatmapSignal) => void;
  focusLens: HeatmapFocusLens;
  onFocusLensChange: (l: HeatmapFocusLens) => void;
  compareScreenName: string;
  onCompareScreenNameChange: (v: string) => void;
  onExitCompare: () => void;
  isLoading: boolean;
  errorMessage: string | null | undefined;
  compareLeftPayload: HeatmapDataResponse | null | undefined;
  compareRightPayload: HeatmapDataResponse | null | undefined;
  compareLeftQualityMetrics: HeatmapQualityMetrics;
  compareRightQualityMetrics: HeatmapQualityMetrics;
  compareSharedMax: number;
}

export function HeatmapComparePanel({
  signal,
  onSignalChange,
  focusLens,
  onFocusLensChange,
  compareScreenName,
  onCompareScreenNameChange,
  onExitCompare,
  isLoading,
  errorMessage,
  compareLeftPayload,
  compareRightPayload,
  compareLeftQualityMetrics,
  compareRightQualityMetrics,
  compareSharedMax,
}: HeatmapComparePanelProps) {
  return (
    <Stack gap="md" className={classes.root}>
      <CompareToolbar
        signal={signal}
        onSignalChange={onSignalChange}
        compareScreenName={compareScreenName}
        onCompareScreenNameChange={onCompareScreenNameChange}
        onExitCompare={onExitCompare}
      />
      <div className={classes.focusBlock}>
        <div className={classes.focusTop}>
          <span className={classes.focusLabel}>Focus</span>
          <Text size="xs" c="teal" fw={600}>
            Advanced
          </Text>
        </div>
        <div className={classes.focusPills}>
          <button
            type="button"
            className={`${classes.pill} ${focusLens === "all" ? classes.pillActive : ""}`}
            onClick={() => onFocusLensChange("all")}
          >
            All interaction data
          </button>
          <button
            type="button"
            className={`${classes.pill} ${focusLens === "key" ? classes.pillActive : ""}`}
            onClick={() => onFocusLensChange("key")}
          >
            Key actions only
          </button>
        </div>
        <Text className={classes.focusHint}>
          All interaction data: density heatmap (glow / signal layers). Key actions: Pulse
          interaction regions as boxes; hover for per-interaction scores.
        </Text>
      </div>
      {isLoading && (
        <Group>
          <Loader size="sm" color="teal" />
          <Text size="sm">Loading compare…</Text>
        </Group>
      )}
      {errorMessage && (
        <Alert color="red" title="Compare failed" icon={<IconInfoCircle />}>
          {errorMessage}
        </Alert>
      )}
      {compareLeftPayload && compareRightPayload && (
        <>
          <div className={classes.compareGrid}>
            <CompareColumn
              title="A"
              data={compareLeftPayload}
              signal={signal}
              focusLens={focusLens}
              sharedWeightMax={compareSharedMax}
            />
            <CompareColumn
              title="B"
              data={compareRightPayload}
              signal={signal}
              focusLens={focusLens}
              sharedWeightMax={compareSharedMax}
            />
          </div>
          <Text size="xs" c="dimmed">
            Shared scale max (weight): {compareSharedMax.toLocaleString()}
          </Text>
          <div className={classes.compareAggregatesGrid}>
            <Box className={classes.compareAggregatesCell}>
              <Text fw={700} mb="sm" size="sm">
                A · {compareLeftPayload.metadata.screenName}
              </Text>
              <HeatmapAggregatesPanel
                payload={compareLeftPayload}
                signal={signal}
                qualityMetrics={compareLeftQualityMetrics}
                focusLens={focusLens}
              />
            </Box>
            <Box className={classes.compareAggregatesCell}>
              <Text fw={700} mb="sm" size="sm">
                B · {compareRightPayload.metadata.screenName}
              </Text>
              <HeatmapAggregatesPanel
                payload={compareRightPayload}
                signal={signal}
                qualityMetrics={compareRightQualityMetrics}
                focusLens={focusLens}
              />
            </Box>
          </div>
        </>
      )}
    </Stack>
  );
}

function CompareToolbar({
  signal,
  onSignalChange,
  compareScreenName,
  onCompareScreenNameChange,
  onExitCompare,
}: {
  signal: HeatmapSignal;
  onSignalChange: (s: HeatmapSignal) => void;
  compareScreenName: string;
  onCompareScreenNameChange: (v: string) => void;
  onExitCompare: () => void;
}) {
  return (
    <Box className={classes.filterBar}>
      <div className={classes.signalHeader}>
        <span className={classes.signalLabel}>Compare screens</span>
        <button
          type="button"
          className={classes.compareCta}
          onClick={onExitCompare}
        >
          Exit compare
        </button>
      </div>
      <div className={classes.chipsRow}>
        {HEATMAP_SIGNALS.map((s) => (
          <button
            key={s.id}
            type="button"
            className={`${classes.chip} ${signal === s.id ? classes.chipActive : ""}`}
            onClick={() => onSignalChange(s.id)}
          >
            {s.label}
          </button>
        ))}
      </div>
      <TextInput
        label="Other screen name"
        value={compareScreenName}
        onChange={(e) => onCompareScreenNameChange(e.currentTarget.value)}
        size="sm"
      />
    </Box>
  );
}

function CompareColumn({
  title,
  data,
  signal,
  focusLens,
  sharedWeightMax,
}: {
  title: string;
  data: HeatmapDataResponse;
  signal: HeatmapSignal;
  focusLens: HeatmapFocusLens;
  sharedWeightMax: number;
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
    <Box>
      <Text fw={700} mb="xs">
        {title}: {data.metadata.screenName}
      </Text>
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
      />
    </Box>
  );
}
