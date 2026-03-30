import type { HeatmapGlowPoint } from "./heatmap.types";
import { useHeatmapBinBudget } from "./useHeatmapBinBudget";
import { HeatmapJsCanvas } from "./HeatmapJsCanvas";
import { HeatmapPhoneFrame } from "./HeatmapPhoneFrame";
import { HeatmapScreenUnderlay } from "./HeatmapScreenUnderlay";
import { HeatmapVizFooter } from "./HeatmapVizFooter";
import { HeatmapVizHeader } from "./HeatmapVizHeader";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapVisualizationProps {
  screenshotUrl: string | null | undefined;
  glowMap: HeatmapGlowPoint[];
  signalLabel?: string;
  totalTapsLabel?: string;
  showFrustrationMarkers?: boolean;
  ragePoints?: Array<{ x: number; y: number; weight: number }>;
  sharedWeightMax?: number;
}

/**
 * heatmap.js canvas over a phone-frame screenshot (fixed overlay geometry).
 */
export function HeatmapVisualization({
  screenshotUrl,
  glowMap,
  signalLabel = "tap",
  totalTapsLabel,
  showFrustrationMarkers = false,
  ragePoints = [],
  sharedWeightMax,
}: HeatmapVisualizationProps) {
  const {
    binBudgetMax,
    binBudget: effectiveBudget,
    setBinBudget,
    displayGlow,
  } = useHeatmapBinBudget(glowMap);

  return (
    <div className={classes.heatViz}>
      <HeatmapVizHeader
        signalLabel={signalLabel}
        totalTapsLabel={totalTapsLabel}
      />

      <HeatmapPhoneFrame>
        <HeatmapScreenUnderlay screenshotUrl={screenshotUrl} />
        <HeatmapJsCanvas
          displayGlow={displayGlow}
          sharedWeightMax={sharedWeightMax}
          showFrustrationMarkers={showFrustrationMarkers}
          ragePoints={ragePoints}
        />
      </HeatmapPhoneFrame>

      <HeatmapVizFooter
        glowMapLength={glowMap.length}
        displayCount={displayGlow.length}
        binBudgetMax={binBudgetMax}
        effectiveBudget={effectiveBudget}
        onBudgetChange={setBinBudget}
      />
    </div>
  );
}
