import type { HeatmapDataResponse } from "./heatmap.types";

const INTERNAL_SCREEN_LABEL = /^__[a-zA-Z0-9_-]+__$/;

function isHeatmapInternalScreenLabel(name: string | undefined): boolean {
  const t = name?.trim();
  return !!t && INTERNAL_SCREEN_LABEL.test(t);
}

/**
 * First non-empty candidate that is not an internal sentinel (e.g. `__empty__`).
 * Use so empty-state copy quotes the screen the user cares about, not mock keys.
 */
export function userFacingHeatmapScreenLabel(
  ...candidates: (string | undefined | null)[]
): string | undefined {
  for (const c of candidates) {
    const t = c?.trim();
    if (!t || isHeatmapInternalScreenLabel(t)) continue;
    return t;
  }
  return undefined;
}

/** No bins in tap, rage, or dead layers — treat as “no heatmap data” for UX. */
export function isHeatmapDataEmpty(payload: HeatmapDataResponse): boolean {
  const glow = payload.layers?.glow_map?.length ?? 0;
  const rage = payload.layers?.frustration_map?.rage?.length ?? 0;
  const dead = payload.layers?.frustration_map?.dead?.length ?? 0;
  return glow === 0 && rage === 0 && dead === 0;
}
