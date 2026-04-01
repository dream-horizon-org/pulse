import type { ReactNode } from "react";
import { Alert, Box, Stack, Text } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import graphClasses from "../components/EngagementGraph.module.css";
import type { HeatmapDataResponse } from "./heatmap.types";
import { HeatmapAggregatesPanel } from "./HeatmapAggregatesPanel";
import classes from "./HeatmapPanel.module.css";
import type { HeatmapPanelProps } from "./heatmapPanel.types";
import {
  HEATMAP_SIGNALS,
  formatAvgTime,
  formatInt,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import type { HeatmapQualityMetrics } from "./heatmapQuality";

export interface HeatmapMainCardProps {
  engagement: HeatmapPanelProps["engagement"];
  signal: HeatmapSignal;
  onSignalChange: (s: HeatmapSignal) => void;
  onCompareClick: () => void;
  focusLens: HeatmapFocusLens;
  onFocusLensChange: (l: HeatmapFocusLens) => void;
  isLoading: boolean;
  errorMessage: string | null | undefined;
  singlePayload: HeatmapDataResponse | null | undefined;
  qualityMetrics: HeatmapQualityMetrics;
  /** Viz + optional bin budget (left column of the map split). */
  mapColumn: ReactNode;
}

export function HeatmapMainCard({
  engagement,
  signal,
  onSignalChange,
  onCompareClick,
  focusLens,
  onFocusLensChange,
  isLoading,
  errorMessage,
  singlePayload,
  qualityMetrics,
  mapColumn,
}: HeatmapMainCardProps) {
  const eventCount =
    singlePayload?.metadata.total_events != null
      ? singlePayload.metadata.total_events.toLocaleString()
      : null;

  return (
    <Stack gap="md">
      <Box className={graphClasses.graphCard}>
        <div className={graphClasses.graphTitle}>Summary</div>
        <Text size="xs" c="dimmed" mb="sm" lh={1.5}>
          Filters and time range match the rest of this screen.
        </Text>

        <div className={classes.summaryMetricsGrid}>
          <div className={graphClasses.metricCard}>
            <Text className={graphClasses.metricLabel}>Events (heatmap scope)</Text>
            <Text className={graphClasses.metricValue}>
              {eventCount ?? "—"}
            </Text>
          </div>
          <div className={graphClasses.metricCard}>
            <Text className={graphClasses.metricLabel}>Sessions</Text>
            <Text className={graphClasses.metricValue}>
              {formatInt(engagement?.totalSessions ?? 0)}
            </Text>
          </div>
          <div className={graphClasses.metricCard}>
            <Text className={graphClasses.metricLabel}>Users</Text>
            <Text className={graphClasses.metricValue}>
              {formatInt(engagement?.totalUsers ?? 0)}
            </Text>
          </div>
          <div className={graphClasses.metricCard}>
            <Text className={graphClasses.metricLabel}>Avg. time</Text>
            <Text className={graphClasses.metricValue}>
              {formatAvgTime(engagement?.avgTimeSpent ?? null)}
            </Text>
          </div>
        </div>
      </Box>

      <Box className={graphClasses.graphCard}>
        <div className={graphClasses.graphTitle}>Map</div>

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
            onClick={onCompareClick}
          >
            Compare screens
          </button>
        </div>

        <div className={classes.filterDivider} />
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
            interaction regions as boxes; hover for per-interaction scores and average on each
            element.
          </Text>
        </div>

        {isLoading && !errorMessage && (
          <div className={classes.loadingSkeleton} />
        )}

        {errorMessage && (
          <Alert
            color="red"
            title="Couldn’t load heatmap"
            icon={<IconInfoCircle />}
            mb="sm"
          >
            {errorMessage}
          </Alert>
        )}

        {!isLoading && !errorMessage && singlePayload && (
          <div className={classes.heatmapSplit}>
            <div className={classes.heatmapSplitLeft}>
              <div className={classes.mapBlock}>{mapColumn}</div>
            </div>
            <div className={classes.heatmapSplitRight}>
              <HeatmapAggregatesPanel
                payload={singlePayload}
                signal={signal}
                qualityMetrics={qualityMetrics}
                focusLens={focusLens}
              />
            </div>
          </div>
        )}
      </Box>
    </Stack>
  );
}
