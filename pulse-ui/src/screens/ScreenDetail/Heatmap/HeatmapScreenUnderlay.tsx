import { HEATMAP_SCREEN_FALLBACK_URL } from "./heatmapViz.constants";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapScreenUnderlayProps {
  screenshotUrl: string | null | undefined;
}

export function HeatmapScreenUnderlay({
  screenshotUrl,
}: HeatmapScreenUnderlayProps) {
  if (screenshotUrl) {
    return (
      <img
        key={screenshotUrl}
        className={classes.screenImg}
        src={screenshotUrl}
        alt=""
        draggable={false}
        onError={(e) => {
          e.currentTarget.src = HEATMAP_SCREEN_FALLBACK_URL;
        }}
      />
    );
  }

  return (
    <div
      style={{
        position: "absolute",
        inset: 12,
        borderRadius: 16,
        background: "#1a1b1e",
      }}
    />
  );
}
