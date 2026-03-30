import { Alert, Box, Stack, Text } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useMemo } from "react";
import graphClasses from "../components/EngagementGraph.module.css";
import type { HeatmapDataResponse, HeatmapGlowPoint } from "./heatmap.types";
import { HeatmapAggregatesPanel } from "./HeatmapAggregatesPanel";
import { HeatmapMapBlock } from "./HeatmapMapBlock";
import classes from "./HeatmapPanel.module.css";
import type { HeatmapPanelProps } from "./heatmapPanel.types";
import {
  HEATMAP_SIGNALS,
  combinedInteractionGlowMap,
  formatAvgTime,
  formatInt,
  glowMapsNearlyEqual,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import type { HeatmapQualityMetrics } from "./heatmapQuality";

export interface HeatmapMainCardProps {
  screenName: string;
  engagement: HeatmapPanelProps["engagement"];
  signal: HeatmapSignal;
  onSignalChange: (s: HeatmapSignal) => void;
  onCompareClick: () => void;
  isLoading: boolean;
  errorMessage: string | null | undefined;
  singlePayload: HeatmapDataResponse | null | undefined;
  qualityMetrics: HeatmapQualityMetrics;
  screenshotUrl: string | null | undefined;
  glowMap: HeatmapGlowPoint[];
  ragePoints: Array<{ x: number; y: number; weight: number }>;
  showFrustrationMarkers: boolean;
}

function capitalizeSignal(s: HeatmapSignal): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function HeatmapMainCard({
  screenName: _screenName,
  engagement,
  signal,
  onSignalChange,
  onCompareClick,
  isLoading,
  errorMessage,
  singlePayload,
  qualityMetrics,
  screenshotUrl,
  glowMap,
  ragePoints,
  showFrustrationMarkers,
}: HeatmapMainCardProps) {
  const eventCount =
    singlePayload?.metadata.total_events != null
      ? singlePayload.metadata.total_events.toLocaleString()
      : null;

  const interactionGlow = useMemo(
    () => combinedInteractionGlowMap(singlePayload ?? null),
    [singlePayload],
  );

  const showInteractionMap = useMemo(() => {
    if (signal !== "tap") return false;
    if (interactionGlow.length === 0) return false;
    return !glowMapsNearlyEqual(glowMap, interactionGlow);
  }, [signal, glowMap, interactionGlow]);

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
              {showInteractionMap ? (
                <Stack gap="md">
                  <HeatmapMapBlock
                    mapLabel={
                      signal === "tap" ? capitalizeSignal(signal) : undefined
                    }
                    screenshotUrl={screenshotUrl}
                    glowMap={glowMap}
                    showFrustrationMarkers={showFrustrationMarkers}
                    ragePoints={ragePoints}
                    intensityLegendAriaLabel={`${capitalizeSignal(signal)} layer activity`}
                    heatmapPalette={
                      signal === "tap" ? "thermal" : "brand"
                    }
                  />
                  <HeatmapMapBlock
                    mapLabel="All interactions"
                    screenshotUrl={screenshotUrl}
                    glowMap={interactionGlow}
                    intensityLegendAriaLabel="Combined interactions activity"
                    heatmapPalette="thermal"
                  />
                </Stack>
              ) : (
                <HeatmapMapBlock
                  hideTitles
                  screenshotUrl={screenshotUrl}
                  glowMap={glowMap}
                  showFrustrationMarkers={showFrustrationMarkers}
                  ragePoints={ragePoints}
                  intensityLegendAriaLabel={`${capitalizeSignal(signal)} layer activity`}
                  heatmapPalette={
                    signal === "tap" ? "thermal" : "brand"
                  }
                />
              )}
            </div>
            <div className={classes.heatmapSplitRight}>
              <HeatmapAggregatesPanel
                payload={singlePayload}
                signal={signal}
                qualityMetrics={qualityMetrics}
              />
            </div>
          </div>
        )}
      </Box>
    </Stack>
  );
}
