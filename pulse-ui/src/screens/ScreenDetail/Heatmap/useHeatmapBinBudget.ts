import { useEffect, useMemo, useState } from "react";
import { topGlowBinsByWeight } from "./heatmapDisplay";
import type { HeatmapGlowPoint } from "./heatmap.types";

export type HeatmapBinBudget = {
  binBudgetMax: number;
  binBudget: number;
  setBinBudget: (v: number) => void;
  displayGlow: HeatmapGlowPoint[];
};

/**
 * Renders the API `glow_map` as returned (no client-side max cap). Optional slider
 * can lower the count for exploration; bounds are only 1 … payload length.
 */
export function useHeatmapBinBudget(glowMap: HeatmapGlowPoint[]): HeatmapBinBudget {
  const binBudgetMax = glowMap.length;
  const [binBudget, setBinBudget] = useState(0);

  useEffect(() => {
    setBinBudget(glowMap.length);
  }, [glowMap.length]);

  const effectiveBudget =
    glowMap.length === 0
      ? 0
      : Math.min(binBudget === 0 ? glowMap.length : binBudget, glowMap.length);

  const displayGlow = useMemo(
    () => topGlowBinsByWeight(glowMap, effectiveBudget),
    [glowMap, effectiveBudget],
  );

  return {
    binBudgetMax,
    binBudget: effectiveBudget,
    setBinBudget,
    displayGlow,
  };
}
