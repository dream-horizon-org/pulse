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
import {
  HEATMAP_SIGNALS,
  glowLayerForSignal,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import { HeatmapVisualization } from "./HeatmapVisualization";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapComparePanelProps {
  signal: HeatmapSignal;
  onSignalChange: (s: HeatmapSignal) => void;
  compareScreenName: string;
  onCompareScreenNameChange: (v: string) => void;
  onExitCompare: () => void;
  isLoading: boolean;
  errorMessage: string | null | undefined;
  compareLeftPayload: HeatmapDataResponse | null | undefined;
  compareRightPayload: HeatmapDataResponse | null | undefined;
  compareSharedMax: number;
}

export function HeatmapComparePanel({
  signal,
  onSignalChange,
  compareScreenName,
  onCompareScreenNameChange,
  onExitCompare,
  isLoading,
  errorMessage,
  compareLeftPayload,
  compareRightPayload,
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
        <div className={classes.compareGrid}>
          <CompareColumn
            title="A"
            data={compareLeftPayload}
            signal={signal}
            sharedWeightMax={compareSharedMax}
          />
          <CompareColumn
            title="B"
            data={compareRightPayload}
            signal={signal}
            sharedWeightMax={compareSharedMax}
          />
        </div>
      )}
      {compareLeftPayload && compareRightPayload && (
        <Text size="xs" c="dimmed">
          Shared scale max (weight): {compareSharedMax.toLocaleString()}
        </Text>
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
  sharedWeightMax,
}: {
  title: string;
  data: HeatmapDataResponse;
  signal: HeatmapSignal;
  sharedWeightMax: number;
}) {
  const glow = glowLayerForSignal(data, signal);
  const map = glow.length ? glow : data.layers.glow_map;

  return (
    <Box>
      <Text fw={700} mb="xs">
        {title}: {data.metadata.screenName}
      </Text>
      <HeatmapVisualization
        screenshotUrl={data.metadata.screenshot_url || undefined}
        glowMap={map}
        signalLabel={signal}
        totalTapsLabel={`${data.metadata.total_events.toLocaleString()} events`}
        sharedWeightMax={sharedWeightMax}
      />
    </Box>
  );
}
