import { useEffect, useMemo, useState } from "react";
import { DEFAULT_HEATMAP_BIN_BUDGET, topGlowBinsByWeight } from "./heatmapDisplay";
import type { HeatmapGlowPoint } from "./heatmap.types";

export function useHeatmapBinBudget(glowMap: HeatmapGlowPoint[]) {
  const binBudgetMax = Math.max(32, glowMap.length);
  const [binBudget, setBinBudget] = useState(() =>
    Math.min(
      DEFAULT_HEATMAP_BIN_BUDGET,
      Math.max(32, glowMap.length || 32),
    ),
  );

  useEffect(() => {
    setBinBudget((prev) =>
      Math.min(Math.max(prev, 32), Math.max(32, glowMap.length)),
    );
  }, [glowMap.length]);

  const effectiveBudget = Math.min(binBudget, binBudgetMax);

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
