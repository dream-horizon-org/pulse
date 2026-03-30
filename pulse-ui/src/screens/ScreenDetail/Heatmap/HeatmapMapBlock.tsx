import { useMemo } from "react";
import { Text } from "@mantine/core";
import type { HeatmapGlowPoint } from "./heatmap.types";
import { type HeatmapPlumePalette, domGlowMapFull } from "./heatmapDomUtils";
import { HeatmapDomPlumeLayer } from "./HeatmapDomPlumeLayer";
import { HeatmapPhoneFrame } from "./HeatmapPhoneFrame";
import { HeatmapScreenUnderlay } from "./HeatmapScreenUnderlay";
import { HeatmapIntensityLegend } from "./HeatmapIntensityLegend";
import graphClasses from "../components/EngagementGraph.module.css";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapMapBlockProps {
  /** Short label when two maps are shown, or one map with a caption. */
  mapLabel?: string;
  hideTitles?: boolean;
  screenshotUrl: string | null | undefined;
  glowMap: HeatmapGlowPoint[];
  showFrustrationMarkers?: boolean;
  ragePoints?: Array<{ x: number; y: number; weight: number }>;
  sharedWeightMax?: number;
  /** Passed to the intensity legend for screen readers. */
  intensityLegendAriaLabel?: string;
  /** Tap: thermal blue→red; rage/dead: brand teal gradient. */
  heatmapPalette?: HeatmapPlumePalette;
}

export function HeatmapMapBlock({
  mapLabel,
  hideTitles = false,
  screenshotUrl,
  glowMap,
  showFrustrationMarkers = false,
  ragePoints = [],
  sharedWeightMax,
  intensityLegendAriaLabel,
  heatmapPalette = "thermal",
}: HeatmapMapBlockProps) {
  const domGlowMap = useMemo(() => domGlowMapFull(glowMap), [glowMap]);

  const domMaxWeight = useMemo(() => {
    const local = domGlowMap.reduce((m, p) => Math.max(m, p.weight), 0);
    if (sharedWeightMax != null && sharedWeightMax > 0) {
      return sharedWeightMax;
    }
    return local || 1;
  }, [domGlowMap, sharedWeightMax]);

  return (
    <div className={classes.mapBlock}>
      {!hideTitles && mapLabel && (
        <Text
          size="xs"
          fw={700}
          c="dark.6"
          tt="uppercase"
          className={classes.mapBlockLabel}
          style={{ letterSpacing: "0.06em" }}
        >
          {mapLabel}
        </Text>
      )}
      <div className={`${graphClasses.chartContainer} ${classes.heatmapChartTight}`}>
        <HeatmapPhoneFrame>
          <HeatmapScreenUnderlay screenshotUrl={screenshotUrl} />
          <HeatmapDomPlumeLayer
            points={domGlowMap}
            maxWeight={domMaxWeight}
            showFrustrationMarkers={showFrustrationMarkers}
            ragePoints={ragePoints}
            palette={heatmapPalette}
          />
        </HeatmapPhoneFrame>
      </div>
      <HeatmapIntensityLegend
        aria-label={intensityLegendAriaLabel}
        variant={heatmapPalette}
      />
    </div>
  );
}
