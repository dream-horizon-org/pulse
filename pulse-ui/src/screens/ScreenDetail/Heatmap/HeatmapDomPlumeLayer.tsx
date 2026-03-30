import type { HeatmapGlowPoint } from "./heatmap.types";
import {
  type HeatmapPlumePalette,
  plumeOpacityForWeight,
  plumeRadialGradientForWeight,
  plumeSizePx,
} from "./heatmapDomUtils";
import { HeatmapFrustrationMarkers } from "./HeatmapFrustrationMarkers";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapDomPlumeLayerProps {
  points: HeatmapGlowPoint[];
  maxWeight: number;
  showFrustrationMarkers: boolean;
  ragePoints: Array<{ x: number; y: number; weight: number }>;
  /** Tap uses thermal (blue→red); rage/dead use brand teal gradient. */
  palette?: HeatmapPlumePalette;
}

export function HeatmapDomPlumeLayer({
  points,
  maxWeight,
  showFrustrationMarkers,
  ragePoints,
  palette = "thermal",
}: HeatmapDomPlumeLayerProps) {
  const scale = maxWeight || 1;

  return (
    <div className={classes.heatOverlay}>
      {points.map((p, i) => {
        const size = plumeSizePx(p.weight, scale);
        const opacity = plumeOpacityForWeight(p.weight, scale, palette);
        return (
          <div
            key={`glow-${i}-${p.x}-${p.y}`}
            className={`${classes.heatPlume} ${classes.heatPlumeIntensity}`}
            style={{
              left: `${p.x * 100}%`,
              top: `${p.y * 100}%`,
              width: size,
              height: size,
              opacity,
              background: plumeRadialGradientForWeight(p.weight, scale, palette),
            }}
          />
        );
      })}
      {showFrustrationMarkers && (
        <HeatmapFrustrationMarkers points={ragePoints} />
      )}
    </div>
  );
}
