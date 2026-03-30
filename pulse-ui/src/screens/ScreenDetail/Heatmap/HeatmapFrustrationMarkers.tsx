import { useMemo } from "react";
import { plumeSizePx } from "./heatmapDomUtils";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapFrustrationMarkersProps {
  points: Array<{ x: number; y: number; weight: number }>;
}

/** Rage clusters as centered radial plumes (no icons). */
export function HeatmapFrustrationMarkers({
  points,
}: HeatmapFrustrationMarkersProps) {
  const maxW = useMemo(
    () => (points.length ? Math.max(...points.map((p) => p.weight), 1) : 1),
    [points],
  );

  return (
    <>
      {points.map((r, i) => {
        const size = plumeSizePx(r.weight, maxW);
        const opacity = 0.35 + (r.weight / maxW) * 0.45;
        return (
          <div
            key={`rage-${i}-${r.x}-${r.y}`}
            className={classes.heatPlume}
            title={`Rage cluster · weight ${r.weight}`}
            style={{
              left: `${r.x * 100}%`,
              top: `${r.y * 100}%`,
              width: size,
              height: size,
              opacity,
              background:
                "radial-gradient(circle, rgba(220,50,45,0.92) 0%, rgba(255,120,90,0.4) 45%, transparent 72%)",
            }}
          />
        );
      })}
    </>
  );
}
