import { useMemo } from "react";
import type { HeatmapGlowPoint } from "./heatmap.types";
import { domGlowMapForBudget } from "./heatmapDomUtils";
import { useHeatmapBinBudget } from "./useHeatmapBinBudget";
import { HeatmapDomPlumeLayer } from "./HeatmapDomPlumeLayer";
import { HeatmapJsCanvas } from "./HeatmapJsCanvas";
import { HeatmapPhoneFrame } from "./HeatmapPhoneFrame";
import { HeatmapScreenUnderlay } from "./HeatmapScreenUnderlay";
import { HeatmapVizFooter } from "./HeatmapVizFooter";
import { HeatmapVizHeader } from "./HeatmapVizHeader";
import type { HeatmapRendererMode } from "./heatmapViz.types";
import classes from "./HeatmapPanel.module.css";

export type { HeatmapRendererMode } from "./heatmapViz.types";

export interface HeatmapVisualizationProps {
  screenshotUrl: string | null | undefined;
  glowMap: HeatmapGlowPoint[];
  signalLabel?: string;
  totalTapsLabel?: string;
  showFrustrationMarkers?: boolean;
  ragePoints?: Array<{ x: number; y: number; weight: number }>;
  sharedWeightMax?: number;
  renderer?: HeatmapRendererMode;
}

/**
 * KDE-style radial overlays on a phone-frame screenshot, or heatmap.js canvas.
 */
export function HeatmapVisualization({
  screenshotUrl,
  glowMap,
  signalLabel = "tap",
  totalTapsLabel,
  showFrustrationMarkers = false,
  ragePoints = [],
  sharedWeightMax,
  renderer = "dom",
}: HeatmapVisualizationProps) {

  console.log( "HeatmapVisualization", {screenshotUrl,
    glowMap,
    signalLabel,
    totalTapsLabel,
    showFrustrationMarkers,
    ragePoints,
    sharedWeightMax,
    renderer});
  const {
    binBudgetMax,
    binBudget: effectiveBudget,
    setBinBudget,
    displayGlow,
  } = useHeatmapBinBudget(glowMap);

  const domGlowMap = useMemo(
    () => domGlowMapForBudget(glowMap, effectiveBudget),
    [glowMap, effectiveBudget],
  );

  const domMaxWeight = useMemo(() => {
    const local = domGlowMap.reduce((m, p) => Math.max(m, p.weight), 0);
    if (sharedWeightMax != null && sharedWeightMax > 0) {
      return sharedWeightMax;
    }
    return local;
  }, [domGlowMap, sharedWeightMax]);

  return (
    <div className={classes.heatViz}>
      <HeatmapVizHeader
        signalLabel={signalLabel}
        renderer={renderer}
        totalTapsLabel={totalTapsLabel}
      />

      <HeatmapPhoneFrame>
        <HeatmapScreenUnderlay screenshotUrl={screenshotUrl} />

        {renderer === "dom" && (
          <HeatmapDomPlumeLayer
            points={domGlowMap}
            maxWeight={domMaxWeight}
            showFrustrationMarkers={showFrustrationMarkers}
            ragePoints={ragePoints}
          />
        )}

        {renderer === "heatmapjs" && (
          <HeatmapJsCanvas
            displayGlow={displayGlow}
            sharedWeightMax={sharedWeightMax}
            showFrustrationMarkers={showFrustrationMarkers}
            ragePoints={ragePoints}
          />
        )}
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
