import type { ReactNode } from "react";
import { Alert, Box, Stack, Text } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import graphClasses from "../components/EngagementGraph.module.css";
import type { HeatmapDataResponse } from "./heatmap.types";
import { HeatmapAggregatesPanel } from "./HeatmapAggregatesPanel";
import classes from "./HeatmapPanel.module.css";
import type { HeatmapPanelProps } from "./heatmapPanel.types";
import {
  formatAvgTime,
  formatInt,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import type { HeatmapQualityMetrics } from "./heatmapQuality";

export interface HeatmapMainCardProps {
  engagement: HeatmapPanelProps["engagement"];
  signal: HeatmapSignal;
  focusLens: HeatmapFocusLens;
  isLoading: boolean;
  errorMessage: string | null | undefined;
  singlePayload: HeatmapDataResponse | null | undefined;
  qualityMetrics: HeatmapQualityMetrics;
  /** Time + filters + view popovers, plus Compare control — built in parent. */
  mapToolbar: ReactNode;
  mapColumn: ReactNode;
}

export function HeatmapMainCard({
  engagement,
  signal,
  focusLens,
  isLoading,
  errorMessage,
  singlePayload,
  qualityMetrics,
  mapToolbar,
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
        <div className={classes.heatmapMapToolbar}>{mapToolbar}</div>

        {isLoading && !errorMessage && (
          <div className={classes.loadingSkeleton} />
        )}

        {errorMessage && (
          <Alert
            color="red"
            title="Heatmap unavailable"
            icon={<IconInfoCircle />}
            mb="sm"
            mt="md"
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
