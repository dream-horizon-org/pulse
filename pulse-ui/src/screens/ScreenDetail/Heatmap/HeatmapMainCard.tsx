import type { ReactNode } from "react";
import { Box, Group, Loader, Stack, Text } from "@mantine/core";
import graphClasses from "../components/EngagementGraph.module.css";
import type { HeatmapDataResponse } from "./heatmap.types";
import { HeatmapAggregatesPanel } from "./HeatmapAggregatesPanel";
import { HeatmapDataEmptyAside } from "./HeatmapDataEmptyAside";
import { HeatmapInvalidTimeRangeAside } from "./HeatmapInvalidTimeRangeAside";
import { HeatmapFetchErrorPanel } from "./HeatmapFetchErrorPanel";
import { isHeatmapDataEmpty } from "./heatmapEmptyState";
import classes from "./HeatmapPanel.module.css";
import {
  HEATMAP_COPY_LOADING_HEATMAP,
  HEATMAP_COPY_METRIC_AVG_TIME,
  HEATMAP_COPY_METRIC_EVENTS,
  HEATMAP_COPY_METRIC_SESSIONS,
  HEATMAP_COPY_METRIC_USERS,
  HEATMAP_COPY_SUMMARY_FILTERS_HINT,
  HEATMAP_COPY_SUMMARY_TITLE,
} from "./heatmapCopy";
import type { HeatmapPanelProps } from "./heatmap.ui.types";
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
  heatmapFetchError: boolean;
  heatmapRetryLoading?: boolean;
  onHeatmapRetry?: () => void;
  singlePayload: HeatmapDataResponse | null | undefined;
  /** Route / context screen for empty-state copy when API uses a sentinel name. */
  contextScreenName: string;
  qualityMetrics: HeatmapQualityMetrics;
  /** Time + filters + view popovers, plus Compare control — built in parent. */
  mapToolbar: ReactNode;
  mapColumn: ReactNode;
  /** True when heatmap From/To are missing or invalid — show empty state instead of hiding the split. */
  invalidTimeRange?: boolean;
}

export function HeatmapMainCard({
  engagement,
  signal,
  focusLens,
  isLoading,
  heatmapFetchError,
  heatmapRetryLoading = false,
  onHeatmapRetry,
  singlePayload,
  contextScreenName,
  qualityMetrics,
  mapToolbar,
  mapColumn,
  invalidTimeRange = false,
}: HeatmapMainCardProps) {
  const eventCount =
    singlePayload?.metadata.total_events != null
      ? singlePayload.metadata.total_events.toLocaleString()
      : null;

  return (
    <Stack gap="md">
      <Box className={graphClasses.graphCard}>
        <div className={graphClasses.graphTitle}>{HEATMAP_COPY_SUMMARY_TITLE}</div>
        <Text size="xs" c="dimmed" mb="sm" lh={1.5}>
          {HEATMAP_COPY_SUMMARY_FILTERS_HINT}
        </Text>

        <div className={classes.summaryMetricsGrid}>
          <div className={graphClasses.metricCard}>
            <Text className={graphClasses.metricLabel}>
              {HEATMAP_COPY_METRIC_EVENTS}
            </Text>
            <Text className={graphClasses.metricValue}>
              {eventCount ?? "—"}
            </Text>
          </div>
          <div className={graphClasses.metricCard}>
            <Text className={graphClasses.metricLabel}>
              {HEATMAP_COPY_METRIC_SESSIONS}
            </Text>
            <Text className={graphClasses.metricValue}>
              {formatInt(engagement?.totalSessions ?? 0)}
            </Text>
          </div>
          <div className={graphClasses.metricCard}>
            <Text className={graphClasses.metricLabel}>
              {HEATMAP_COPY_METRIC_USERS}
            </Text>
            <Text className={graphClasses.metricValue}>
              {formatInt(engagement?.totalUsers ?? 0)}
            </Text>
          </div>
          <div className={graphClasses.metricCard}>
            <Text className={graphClasses.metricLabel}>
              {HEATMAP_COPY_METRIC_AVG_TIME}
            </Text>
            <Text className={graphClasses.metricValue}>
              {formatAvgTime(engagement?.avgTimeSpent ?? null)}
            </Text>
          </div>
        </div>
      </Box>

      <Box className={graphClasses.graphCard}>
        <div className={classes.heatmapMapToolbar}>{mapToolbar}</div>

        {heatmapFetchError && (
          <HeatmapFetchErrorPanel
            onRetry={onHeatmapRetry}
            retryLoading={heatmapRetryLoading}
          />
        )}

        {!heatmapFetchError && invalidTimeRange && (
          <div className={classes.heatmapSplit}>
            <div className={classes.heatmapSplitLeft}>
              <div className={classes.mapBlock}>
                <HeatmapInvalidTimeRangeAside />
              </div>
            </div>
            <div className={classes.heatmapSplitRight}>
              <HeatmapInvalidTimeRangeAside />
            </div>
          </div>
        )}

        {!heatmapFetchError && !invalidTimeRange && isLoading && (
          <div className={classes.heatmapMainLoading}>
            <Group gap="sm" py="md" justify="center" wrap="nowrap" w="100%">
              <Loader size="sm" color="teal" />
              <Text size="sm" c="dimmed">
                {HEATMAP_COPY_LOADING_HEATMAP}
              </Text>
            </Group>
            <div className={classes.loadingSkeleton} />
          </div>
        )}

        {!isLoading && !heatmapFetchError && !invalidTimeRange && singlePayload && (
          <div className={classes.heatmapSplit}>
            <div className={classes.heatmapSplitLeft}>
              <div className={classes.mapBlock}>{mapColumn}</div>
            </div>
            <div className={classes.heatmapSplitRight}>
              {isHeatmapDataEmpty(singlePayload) ? (
                <HeatmapDataEmptyAside
                  screenName={singlePayload.metadata.screenName}
                  contextScreenName={contextScreenName}
                />
              ) : (
                <HeatmapAggregatesPanel
                  payload={singlePayload}
                  signal={signal}
                  qualityMetrics={qualityMetrics}
                  focusLens={focusLens}
                />
              )}
            </div>
          </div>
        )}
      </Box>
    </Stack>
  );
}
