import { HeatmapMapBlock } from "./HeatmapMapBlock";
import { HeatmapVizHeader } from "./HeatmapVizHeader";
import graphClasses from "../components/EngagementGraph.module.css";
import classes from "./HeatmapPanel.module.css";
import type { HeatmapGlowPoint } from "./heatmap.types";

export interface HeatmapVisualizationProps {
  screenshotUrl: string | null | undefined;
  glowMap: HeatmapGlowPoint[];
  screenName?: string;
  signalLabel?: string;
  totalTapsLabel?: string;
  showFrustrationMarkers?: boolean;
  ragePoints?: Array<{ x: number; y: number; weight: number }>;
  sharedWeightMax?: number;
  embedded?: boolean;
}

/**
 * Compare / standalone map: optional header card + map + intensity legend (no slider).
 */
export function HeatmapVisualization({
  screenshotUrl,
  glowMap,
  screenName,
  signalLabel = "tap",
  totalTapsLabel,
  showFrustrationMarkers = false,
  ragePoints = [],
  sharedWeightMax,
  embedded = false,
}: HeatmapVisualizationProps) {
  const inner = (
    <>
      {!embedded && (
        <HeatmapVizHeader
          screenName={screenName}
          signalLabel={signalLabel}
          totalTapsLabel={totalTapsLabel}
        />
      )}
      <HeatmapMapBlock
        hideTitles
        screenshotUrl={screenshotUrl}
        glowMap={glowMap}
        showFrustrationMarkers={showFrustrationMarkers}
        ragePoints={ragePoints}
        sharedWeightMax={sharedWeightMax}
      />
    </>
  );

  if (embedded) {
    return <div className={classes.heatVizEmbedded}>{inner}</div>;
  }

  return (
    <div className={`${graphClasses.graphCard} ${classes.heatViz}`}>
      {inner}
    </div>
  );
}
