import type { HeatmapGlowPoint } from "./heatmap.types";
import { plumeSizePx } from "./heatmapDomUtils";
import { HeatmapFrustrationMarkers } from "./HeatmapFrustrationMarkers";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapDomPlumeLayerProps {
  points: HeatmapGlowPoint[];
  maxWeight: number;
  showFrustrationMarkers: boolean;
  ragePoints: Array<{ x: number; y: number; weight: number }>;
}

export function HeatmapDomPlumeLayer({
  points,
  maxWeight,
  showFrustrationMarkers,
  ragePoints,
}: HeatmapDomPlumeLayerProps) {
  const scale = maxWeight || 1;

  return (
    <div className={classes.heatOverlay}>
      {points.map((p, i) => {
        const size = plumeSizePx(p.weight, scale);
        const opacity = 0.25 + (p.weight / scale) * 0.55;
        return (
          <div
            key={`glow-${i}-${p.x}-${p.y}`}
            className={classes.heatPlume}
            style={{
              left: `${p.x * 100}%`,
              top: `${p.y * 100}%`,
              width: size,
              height: size,
              opacity,
              background:
                "radial-gradient(circle, rgba(255,80,70,0.85) 0%, rgba(255,160,80,0.35) 42%, transparent 72%)",
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
