import {
  Box,
  Stack,
  Group,
  Text,
  Loader,
  Alert,
  TextInput,
  Paper,
} from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import type { HeatmapDataResponse } from "./heatmap.types";
import {
  HEATMAP_SIGNALS,
  glowLayerForSignal,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import { HeatmapAggregatesPanel } from "./HeatmapAggregatesPanel";
import { HeatmapMapBlock } from "./HeatmapMapBlock";
import graphClasses from "../components/EngagementGraph.module.css";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapComparePanelProps {
  currentScreenName: string;
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
  currentScreenName,
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
    <Stack gap="md">
      <Box className={graphClasses.graphCard}>
        <div className={graphClasses.graphTitle}>Compare screens</div>
        <Text size="xs" c="dimmed" mb="sm">
          Same time range and header filters as this page. Pick a signal, then
          compare this screen with another by name.
        </Text>
        <div className={classes.controlsRow}>
          <div className={classes.controlsLeft}>
            <Text
              size="xs"
              fw={700}
              c="#0ba09a"
              tt="uppercase"
              className={classes.controlsLabel}
            >
              Signal
            </Text>
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
          </div>
          <button
            type="button"
            className={classes.compareCta}
            onClick={onExitCompare}
          >
            Exit compare
          </button>
        </div>
        <Group align="flex-end" gap="md" wrap="wrap" mt="md">
          <TextInput
            label="This screen"
            value={currentScreenName}
            readOnly
            size="sm"
            style={{ flex: "1 1 200px" }}
          />
          <TextInput
            label="Compare with"
            placeholder="Other screen name"
            value={compareScreenName}
            onChange={(e) => onCompareScreenNameChange(e.currentTarget.value)}
            size="sm"
            style={{ flex: "1 1 200px" }}
          />
        </Group>
      </Box>

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
          <div className={classes.comparePairRow}>
            <ScreenContextCard data={compareLeftPayload} role="A" />
            <ScreenContextCard data={compareRightPayload} role="B" />
          </div>

          <div className={classes.comparePairRow}>
            <CompareMapColumn
              data={compareLeftPayload}
              signal={signal}
              sharedWeightMax={compareSharedMax}
            />
            <CompareMapColumn
              data={compareRightPayload}
              signal={signal}
              sharedWeightMax={compareSharedMax}
            />
          </div>

          <Text size="xs" c="dimmed" ta="center">
            Shared color scale (max weight):{" "}
            {compareSharedMax.toLocaleString()}
          </Text>

          <div className={classes.comparePairRow}>
            <div className={classes.compareAggregatesSlot}>
              <HeatmapAggregatesPanel
                payload={compareLeftPayload}
                signal={signal}
              />
            </div>
            <div className={classes.compareAggregatesSlot}>
              <HeatmapAggregatesPanel
                payload={compareRightPayload}
                signal={signal}
              />
            </div>
          </div>
        </>
      )}
    </Stack>
  );
}

function ScreenContextCard({
  data,
  role,
}: {
  data: HeatmapDataResponse;
  role: "A" | "B";
}) {
  const m = data.metadata;
  const metaLine = [m.app_version, m.platform].filter(Boolean).join(" · ");
  return (
    <Paper className={classes.compareContextCard} p="sm" withBorder radius="md">
      <Text size="xs" fw={700} c="#0ba09a" tt="uppercase" mb={4}>
        Screen {role}
      </Text>
      <Text fw={700} size="sm">
        {m.screenName}
      </Text>
      <Text size="xs" c="dimmed" mt={4}>
        {metaLine || "—"}
      </Text>
      <Text size="xs" c="dimmed" mt={4}>
        Events in scope: {m.total_events.toLocaleString()}
      </Text>
    </Paper>
  );
}

function CompareMapColumn({
  data,
  signal,
  sharedWeightMax,
}: {
  data: HeatmapDataResponse;
  signal: HeatmapSignal;
  sharedWeightMax: number;
}) {
  const glow = glowLayerForSignal(data, signal);
  const map = glow.length ? glow : data.layers.glow_map;
  const ragePoints =
    data.layers.frustration_map?.rage?.map((r) => ({
      x: r.x,
      y: r.y,
      weight: r.weight,
    })) ?? [];

  return (
    <Box className={classes.compareMapSlot}>
      <HeatmapMapBlock
        hideTitles
        screenshotUrl={data.metadata.screenshot_url || undefined}
        glowMap={map}
        showFrustrationMarkers={signal === "rage"}
        ragePoints={ragePoints}
        sharedWeightMax={sharedWeightMax}
        intensityLegendAriaLabel={`${data.metadata.screenName} heatmap`}
        heatmapPalette={signal === "tap" ? "thermal" : "brand"}
      />
    </Box>
  );
}
