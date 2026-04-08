import type { HeatmapFrustrationEmojiMarkersConfig } from "./heatmapPanelUtils";

export interface HeatmapFrustrationMarkersProps {
  points: HeatmapFrustrationEmojiMarkersConfig["points"];
  kind: HeatmapFrustrationEmojiMarkersConfig["kind"];
}

export function HeatmapFrustrationMarkers({
  points,
  kind,
}: HeatmapFrustrationMarkersProps) {
  const emoji = kind === "rage" ? "😡" : "👻";
  const label = kind === "rage" ? "Rage cluster" : "Dead click";
  const keyPrefix = kind === "rage" ? "rage" : "dead";

  return (
    <>
      {points.map((r, i) => (
        <div
          key={`${keyPrefix}-${i}`}
          style={{
            position: "absolute",
            left: `${r.x * 100}%`,
            top: `${r.y * 100}%`,
            transform: "translate(-50%, -100%)",
            fontSize: 14,
          }}
          title={`${label} · weight ${r.weight}`}
        >
          {emoji}
        </div>
      ))}
    </>
  );
}
