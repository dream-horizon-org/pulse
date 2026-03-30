export interface HeatmapFrustrationMarkersProps {
  points: Array<{ x: number; y: number; weight: number }>;
}

export function HeatmapFrustrationMarkers({
  points,
}: HeatmapFrustrationMarkersProps) {
  return (
    <>
      {points.map((r, i) => (
        <div
          key={`rage-${i}`}
          style={{
            position: "absolute",
            left: `${r.x * 100}%`,
            top: `${r.y * 100}%`,
            transform: "translate(-50%, -100%)",
            fontSize: 14,
          }}
          title={`Rage cluster · weight ${r.weight}`}
        >
          😡
        </div>
      ))}
    </>
  );
}
