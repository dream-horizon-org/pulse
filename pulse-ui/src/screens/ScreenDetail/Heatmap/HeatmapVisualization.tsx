import { useCallback, useEffect, useState } from "react";
import graphClasses from "../components/EngagementGraph.module.css";
import type {
  HeatmapGlowPoint,
  HeatmapInteractionElementRegion,
} from "./heatmap.types";
import type { HeatmapBinBudget } from "./useHeatmapBinBudget";
import { HeatmapGlowBinHoverLayer } from "./HeatmapGlowBinHoverLayer";
import { HeatmapInteractionOverlay } from "./HeatmapInteractionOverlay";
import { HeatmapJsCanvas } from "./HeatmapJsCanvas";
import { HeatmapPhoneFrame } from "./HeatmapPhoneFrame";
import { HeatmapScreenshotStage } from "./HeatmapScreenshotStage";
import { HeatmapScreenUnderlay } from "./HeatmapScreenUnderlay";
import { HeatmapVizFooter } from "./HeatmapVizFooter";
import type { HeatmapFocusLens, HeatmapSignal } from "./heatmapPanelUtils";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapVisualizationProps {
  screenshotUrls: string[];
  glowMap: HeatmapGlowPoint[];
  binBudget: HeatmapBinBudget;
  showDensityFooter?: boolean;
  showFrustrationMarkers?: boolean;
  ragePoints?: Array<{ x: number; y: number; weight: number }>;
  sharedWeightMax?: number;
  /** All interaction data → density heatmap; Key actions → interaction rectangles. */
  focusLens?: HeatmapFocusLens;
  interactionRegions?: HeatmapInteractionElementRegion[];
  /**
   * When true, sits inside HeatmapMainCard chart area only (no outer viz card).
   */
  embedded?: boolean;
  /** Chooses density legend gradient (tap → thermal, others → brand teal–red). */
  signal?: HeatmapSignal;
}

/**
 * Phone-frame viz: glow heatmap (all) or Pulse interaction regions (key).
 */
export function HeatmapVisualization({
  screenshotUrls,
  glowMap,
  binBudget,
  showDensityFooter = true,
  showFrustrationMarkers = false,
  ragePoints = [],
  sharedWeightMax,
  focusLens = "all",
  interactionRegions = [],
  embedded = false,
  signal = "tap",
}: HeatmapVisualizationProps) {
  const { displayGlow, binBudgetMax, binBudget: effectiveBudget, setBinBudget } =
    binBudget;

  const keyActionsView = focusLens === "key";
  const densityGradientVariant = signal === "tap" ? "thermal" : "brand";

  const [shotIndex, setShotIndex] = useState(0);
  const urlsKey = screenshotUrls.join("\0");
  const count = screenshotUrls.length;

  useEffect(() => {
    setShotIndex(0);
  }, [urlsKey]);

  useEffect(() => {
    setShotIndex((i) => (count <= 0 ? 0 : Math.min(i, count - 1)));
  }, [count]);
  const safeIndex = count > 0 ? Math.min(shotIndex, count - 1) : 0;
  const activeScreenshotUrl = count > 0 ? screenshotUrls[safeIndex] : undefined;

  const goPrev = useCallback(() => {
    if (count <= 1) return;
    setShotIndex((i) => (i - 1 + count) % count);
  }, [count]);

  const goNext = useCallback(() => {
    if (count <= 1) return;
    setShotIndex((i) => (i + 1) % count);
  }, [count]);

  const stage = (
    <HeatmapScreenshotStage
      count={count}
      onPrev={goPrev}
      onNext={goNext}
      densityGradientVariant={densityGradientVariant}
      frame={
        <HeatmapPhoneFrame>
          <HeatmapScreenUnderlay screenshotUrl={activeScreenshotUrl} />
          {keyActionsView ? (
            <HeatmapInteractionOverlay regions={interactionRegions} />
          ) : (
            <>
              <HeatmapJsCanvas
                displayGlow={displayGlow}
                sharedWeightMax={sharedWeightMax}
                showFrustrationMarkers={showFrustrationMarkers}
                ragePoints={ragePoints}
              />
              <HeatmapGlowBinHoverLayer points={displayGlow} />
            </>
          )}
        </HeatmapPhoneFrame>
      }
    />
  );

  if (embedded) {
    return (
      <div className={`${graphClasses.chartContainer} ${classes.heatmapChartTight}`}>
        {stage}
      </div>
    );
  }

  return (
    <div className={classes.heatViz}>
      {stage}

      {showDensityFooter && (
        <HeatmapVizFooter
          glowMapLength={glowMap.length}
          displayCount={displayGlow.length}
          binBudgetMax={binBudgetMax}
          effectiveBudget={effectiveBudget}
          onBudgetChange={setBinBudget}
        />
      )}
    </div>
  );
}
