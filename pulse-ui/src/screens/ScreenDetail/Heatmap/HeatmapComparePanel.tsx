import { type ReactNode } from "react";
import {
  Box,
  Divider,
  Group,
  Loader,
  Alert,
  Select,
  Stack,
  Text,
} from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import type { HeatmapDataResponse } from "./heatmap.types";
import { screenshotUrlsFromMetadata } from "./heatmapMetadataUtils";
import {
  glowLayerForSignal,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import { HeatmapVisualization } from "./HeatmapVisualization";
import { useHeatmapBinBudget } from "./useHeatmapBinBudget";
import { HeatmapAggregatesPanel } from "./HeatmapAggregatesPanel";
import type { HeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapMapViewControls } from "./HeatmapMapViewControls";
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
  isLoading: boolean;
  errorMessage: string | null | undefined;
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
  isLoading,
  errorMessage,
  compareLeftPayload,
  compareRightPayload,
  compareLeftQualityMetrics,
  compareRightQualityMetrics,
  compareSharedMax,
  showInteractionMapOption = true,
}: HeatmapComparePanelProps) {
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

      {isLoading && (
        <Group>
          <Loader size="sm" color="teal" />
          <Text size="sm">Loading comparison…</Text>
        </Group>
      )}
      {errorMessage && (
        <Alert color="red" title="Couldn't load comparison" icon={<IconInfoCircle />}>
          {errorMessage}
        </Alert>
      )}
      {compareLeftPayload && compareRightPayload && (
        <>
          <div className={classes.compareGrid}>
            <CompareColumn
              data={compareLeftPayload}
              signal={signal}
              focusLens={focusLens}
              sharedWeightMax={compareSharedMax}
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
            />
            <CompareColumn
              data={compareRightPayload}
              signal={signal}
              focusLens={focusLens}
              sharedWeightMax={compareSharedMax}
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
            />
          </div>
          <div className={classes.compareAggregatesGrid}>
            <Box className={classes.compareAggregatesCell}>
              <Text fw={700} mb="sm" size="sm">
                {screenAName}
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
                {compareRightPayload.metadata.screenName}
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

function CompareColumn({
  headerSlot,
  data,
  signal,
  focusLens,
  sharedWeightMax,
}: {
  headerSlot: ReactNode;
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
      {headerSlot}
      <Box mt="md">
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
          densityBinTooltip={{ payload: data, signal }}
        />
      </Box>
    </Box>
  );
}
