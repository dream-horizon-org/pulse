import { Alert, Box, Group, SimpleGrid, Stack, Text } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useMemo } from "react";
import graphClasses from "../components/EngagementGraph.module.css";
import type { HeatmapDataResponse, HeatmapGlowPoint } from "./heatmap.types";
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
import { HeatmapQualityInline } from "./HeatmapQualityInline";

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
  screenName,
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
  const displayName = screenName.trim() || "This screen";
  const eventCount =
    singlePayload?.metadata.total_events != null
      ? singlePayload.metadata.total_events.toLocaleString()
      : null;

  const interactionGlow = useMemo(
    () => combinedInteractionGlowMap(singlePayload ?? null),
    [singlePayload],
  );

  const showInteractionMap = useMemo(() => {
    if (interactionGlow.length === 0) return false;
    return !glowMapsNearlyEqual(glowMap, interactionGlow);
  }, [glowMap, interactionGlow]);

  return (
    <Stack gap="md">
      <Box className={graphClasses.graphCard}>
        <div className={graphClasses.graphTitle}>Summary</div>
        <Text size="xs" mb="sm" lh={1.5}>
          <Text span fw={700} c="dark.6">
            {displayName}
          </Text>
          <Text span c="dimmed">
            {" "}
            · Same filters and time range as other tabs
          </Text>
        </Text>

        <div className={classes.contextStrip}>
          <Group gap="xs" wrap="wrap" align="center">
            <Text size="sm" span>
              <Text span fw={600} c="dimmed">
                Avg. time ·{" "}
              </Text>
              <Text span fw={600}>
                {formatAvgTime(engagement?.avgTimeSpent ?? null)}
              </Text>
            </Text>
            <Text span c="dimmed" size="sm">
              ·
            </Text>
            <Text size="sm" span>
              <Text span fw={600} c="dimmed">
                Sessions ·{" "}
              </Text>
              <Text span fw={600}>
                {formatInt(engagement?.totalSessions ?? 0)}
              </Text>
            </Text>
            <Text span c="dimmed" size="sm">
              ·
            </Text>
            <Text size="sm" span>
              <Text span fw={600} c="dimmed">
                Users ·{" "}
              </Text>
              <Text span fw={600}>
                {formatInt(engagement?.totalUsers ?? 0)}
              </Text>
            </Text>
            {eventCount != null && (
              <>
                <Text span c="dimmed" size="sm">
                  ·
                </Text>
                <Text size="sm" span>
                  <Text span fw={600} c="dimmed">
                    Events ·{" "}
                  </Text>
                  <Text span fw={600}>
                    {eventCount}
                  </Text>
                </Text>
              </>
            )}
          </Group>
        </div>

        <HeatmapQualityInline
          singlePayload={singlePayload}
          qualityMetrics={qualityMetrics}
        />
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
          <SimpleGrid
            cols={{ base: 1, md: showInteractionMap ? 2 : 1 }}
            spacing="md"
            mt="md"
          >
            <HeatmapMapBlock
              hideTitles={!showInteractionMap}
              mapLabel={showInteractionMap ? capitalizeSignal(signal) : undefined}
              screenshotUrl={screenshotUrl}
              glowMap={glowMap}
              showFrustrationMarkers={showFrustrationMarkers}
              ragePoints={ragePoints}
              intensityLegendAriaLabel={`${capitalizeSignal(signal)} layer activity`}
            />
            {showInteractionMap && (
              <HeatmapMapBlock
                mapLabel="All interactions"
                screenshotUrl={screenshotUrl}
                glowMap={interactionGlow}
                intensityLegendAriaLabel="Combined interactions activity"
              />
            )}
          </SimpleGrid>
        )}
      </Box>
    </Stack>
  );
}
