import { useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { Stack, Alert } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useHeatmapData } from "../../../hooks/useHeatmapData";
import { useFilterStore } from "../../../stores/useFilterStore";
import { ROUTES } from "../../../constants";
import { getHeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapVisualization } from "./HeatmapVisualization";
import { HeatmapFilterBar } from "./HeatmapFilterBar";
import { HeatmapScoreSection } from "./HeatmapScoreSection";
import { HeatmapComparePanel } from "./HeatmapComparePanel";
import { HeatmapDrillDown, HeatmapEngagementCards } from "./HeatmapPanelFooter";
import {
  compareSharedWeightMax,
  glowLayerForSignal,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import type { HeatmapPanelProps } from "./heatmapPanel.types";
import classes from "./HeatmapPanel.module.css";

export type { HeatmapPanelProps } from "./heatmapPanel.types";

/**
 * Heatmap tab — layout from wireframes/heatmap/frames.pen (sdHeatBody).
 */
export function HeatmapPanel({
  screenName,
  startTime,
  endTime,
  engagement,
}: HeatmapPanelProps) {
  const { projectId } = useParams<{ projectId: string }>();
  const { filterValues } = useFilterStore();
  const [compareEnabled, setCompareEnabled] = useState(false);
  const [compareScreenName, setCompareScreenName] = useState("HomeScreen");
  const [signal, setSignal] = useState<HeatmapSignal>("tap");
  const [focusLens, setFocusLens] = useState<HeatmapFocusLens>("all");

  const heatmapRequestFilters = useMemo(
    () => ({
      app_version: filterValues?.APP_VERSION?.trim() || undefined,
      platform: filterValues?.PLATFORM?.trim() || undefined,
    }),
    [filterValues],
  );

  const compareSliceReady =
    compareEnabled && !!compareScreenName.trim();

  const heatmapQuery = useHeatmapData({
    screenName,
    startTime,
    endTime,
    ...heatmapRequestFilters,
    enabled: !compareEnabled,
  });

  const compareLeftQuery = useHeatmapData({
    screenName,
    startTime,
    endTime,
    ...heatmapRequestFilters,
    enabled: compareSliceReady,
  });

  const compareRightQuery = useHeatmapData({
    screenName: compareScreenName.trim() || "HomeScreen",
    startTime,
    endTime,
    ...heatmapRequestFilters,
    enabled: compareSliceReady,
  });

  const singlePayload = heatmapQuery.data?.data;
  const singleErr = heatmapQuery.data?.error;
  const compareLeftPayload = compareLeftQuery.data?.data;
  const compareRightPayload = compareRightQuery.data?.data;
  const compareLeftErr = compareLeftQuery.data?.error;
  const compareRightErr = compareRightQuery.data?.error;

  const compareSharedMax = useMemo(
    () =>
      compareSharedWeightMax(compareLeftPayload, compareRightPayload, signal),
    [compareLeftPayload, compareRightPayload, signal],
  );

  const qualityMetrics = useMemo(
    () => getHeatmapQualityMetrics(singlePayload ?? null),
    [singlePayload],
  );

  const glowForSignal = useMemo(
    () => glowLayerForSignal(singlePayload ?? null, signal),
    [singlePayload, signal],
  );

  const rageForMarkers =
    singlePayload?.layers?.frustration_map?.rage?.map((r) => ({
      x: r.x,
      y: r.y,
      weight: r.weight,
    })) ?? [];

  const userEngagementPath = projectId
    ? ROUTES.PROJECT_USER_ENGAGEMENT.path.replace(":projectId", projectId)
    : "#";
  const appVitalsPath = projectId
    ? ROUTES.PROJECT_APP_VITALS.path.replace(":projectId", projectId)
    : "#";

  const compareErrorMessage =
    compareLeftErr || compareRightErr || compareLeftQuery.isError || compareRightQuery.isError
      ? (compareLeftErr?.message ??
        compareRightErr?.message ??
        "Request failed")
      : null;

  if (compareEnabled) {
    return (
      <HeatmapComparePanel
        signal={signal}
        onSignalChange={setSignal}
        compareScreenName={compareScreenName}
        onCompareScreenNameChange={setCompareScreenName}
        onExitCompare={() => setCompareEnabled(false)}
        isLoading={
          compareLeftQuery.isLoading || compareRightQuery.isLoading
        }
        errorMessage={compareErrorMessage}
        compareLeftPayload={compareLeftPayload}
        compareRightPayload={compareRightPayload}
        compareSharedMax={compareSharedMax}
      />
    );
  }

  return (
    <Stack gap="md" className={classes.root}>
      <HeatmapFilterBar
        signal={signal}
        onSignalChange={setSignal}
        onCompareClick={() => setCompareEnabled(true)}
        focusLens={focusLens}
        onFocusLensChange={setFocusLens}
      />

      <HeatmapScoreSection
        singlePayload={singlePayload}
        qualityMetrics={qualityMetrics}
        focusLens={focusLens}
      />

      {heatmapQuery.isLoading && (
        <div className={classes.loadingSkeleton} />
      )}

      {(singleErr || heatmapQuery.isError) && (
        <Alert
          color="red"
          title="Heatmap request failed"
          icon={<IconInfoCircle />}
        >
          {singleErr?.message ?? "Request failed"}
        </Alert>
      )}

      {singlePayload && (
        <HeatmapVisualization
          screenshotUrl={singlePayload.metadata.screenshot_url || undefined}
          glowMap={glowForSignal}
          signalLabel={signal}
          totalTapsLabel={
            singlePayload.metadata.total_events
              ? `${singlePayload.metadata.total_events.toLocaleString()} events in range`
              : undefined
          }
          showFrustrationMarkers={signal === "rage"}
          ragePoints={rageForMarkers}
        />
      )}

      <HeatmapDrillDown
        userEngagementPath={userEngagementPath}
        appVitalsPath={appVitalsPath}
      />

      <HeatmapEngagementCards engagement={engagement} />
    </Stack>
  );
}
