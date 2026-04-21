import { useCallback, useEffect, useMemo, useState } from "react";
import { topGlowBinsByWeight } from "./heatmapDisplay";
import type { HeatmapGlowPoint } from "./heatmap.types";

export type HeatmapBinBudget = {
  binBudgetMax: number;
  binBudget: number;
  setBinBudget: (v: number) => void;
  displayGlow: HeatmapGlowPoint[];
};

/**
 * Renders the API `glow_map` as returned (no client-side max cap).
 * When `binBudgetControlsEnabled` (mock-only), a slider can lower the count for QA;
 * otherwise the full layer is always shown and `setBinBudget` is a no-op.
 */
export function useHeatmapBinBudget(
  glowMap: HeatmapGlowPoint[],
  binBudgetControlsEnabled = false,
): HeatmapBinBudget {
  const binBudgetMax = glowMap.length;
  const [binBudget, setBinBudget] = useState(0);
  const noopSetBinBudget = useCallback(() => {}, []);

  useEffect(() => {
    setBinBudget(glowMap.length);
  }, [glowMap.length]);

  const effectiveBudget =
    !binBudgetControlsEnabled || glowMap.length === 0
      ? glowMap.length
      : Math.min(binBudget === 0 ? glowMap.length : binBudget, glowMap.length);

  const displayGlow = useMemo(
    () => topGlowBinsByWeight(glowMap, effectiveBudget),
    [glowMap, effectiveBudget],
  );

  return {
    binBudgetMax,
    binBudget: effectiveBudget,
    setBinBudget: binBudgetControlsEnabled ? setBinBudget : noopSetBinBudget,
    displayGlow,
  };
}
