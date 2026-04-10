import { useCallback, useEffect, useState } from "react";
import graphClasses from "../components/EngagementGraph.module.css";
import type {
  HeatmapDataResponse,
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
  /**
   * Resolved display URLs (`data:`, `blob:`, or direct image href) for `<img src>`.
   * May be empty briefly while JSON capture manifests load; use `screenshotCarouselCount` for carousel size.
   */
  screenshotUrls: string[];
  /** Length of `screenshot_urls` from API when it differs from `screenshotUrls.length` during resolve. */
  screenshotCarouselCount?: number;
  /** Bumps carousel index reset when raw manifest list changes (e.g. `sourceKey` from `useResolvedHeatmapScreenshots`). */
  screenshotSourceKey?: string;
  /** True while captures are being fetched/decoded. */
  screenshotsLoading?: boolean;
  glowMap: HeatmapGlowPoint[];
  binBudget: HeatmapBinBudget;
  showDensityFooter?: boolean;
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
  /** Enables bin tooltip: total events at spot + highlighted active layer count. */
  densityBinTooltip?: {
    payload: HeatmapDataResponse;
    signal: HeatmapSignal;
  };
  /** Local filter viewport bucket — adjusts phone frame aspect/width when set. */
  breakpoint?: string;
}

/**
 * Phone-frame viz: glow heatmap (all) or Pulse interaction regions (key).
 */
export function HeatmapVisualization({
  screenshotUrls,
  screenshotCarouselCount,
  screenshotSourceKey,
  screenshotsLoading = false,
  glowMap,
  binBudget,
  showDensityFooter = true,
  sharedWeightMax,
  focusLens = "all",
  interactionRegions = [],
  embedded = false,
  signal = "tap",
  densityBinTooltip,
  breakpoint = "",
}: HeatmapVisualizationProps) {
  const { displayGlow, binBudgetMax, binBudget: effectiveBudget, setBinBudget } =
    binBudget;

  const keyActionsView = focusLens === "key";
  const densityGradientVariant = signal === "tap" ? "thermal" : "brand";

  const [shotIndex, setShotIndex] = useState(0);
  const urlsKey =
    screenshotSourceKey ?? screenshotUrls.join("\0");
  const count =
    screenshotCarouselCount ?? screenshotUrls.length;

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
      activeIndex={count > 0 ? safeIndex + 1 : undefined}
      onPrev={goPrev}
      onNext={goNext}
      densityGradientVariant={densityGradientVariant}
      frame={
        <HeatmapPhoneFrame breakpoint={breakpoint}>
          <HeatmapScreenUnderlay
            screenshotUrl={activeScreenshotUrl}
            loading={
              screenshotsLoading && count > 0 && !activeScreenshotUrl
            }
          />
          {keyActionsView ? (
            <HeatmapInteractionOverlay regions={interactionRegions} />
          ) : (
            <>
              <HeatmapJsCanvas
                displayGlow={displayGlow}
                sharedWeightMax={sharedWeightMax}
              />
              <HeatmapGlowBinHoverLayer
                points={displayGlow}
                binTooltip={densityBinTooltip}
              />
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
