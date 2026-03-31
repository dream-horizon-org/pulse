import { Box, Text } from "@mantine/core";
import {
  HEATMAP_SIGNALS,
  type HeatmapFocusLens,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapFilterBarProps {
  signal: HeatmapSignal;
  onSignalChange: (s: HeatmapSignal) => void;
  onCompareClick: () => void;
  focusLens: HeatmapFocusLens;
  onFocusLensChange: (lens: HeatmapFocusLens) => void;
}

export function HeatmapFilterBar({
  signal,
  onSignalChange,
  onCompareClick,
  focusLens,
  onFocusLensChange,
}: HeatmapFilterBarProps) {
  return (
    <Box className={classes.filterBar}>
      <div className={classes.signalHeader}>
        <span className={classes.signalLabel}>What to show on the map</span>
        <button
          type="button"
          className={classes.compareCta}
          onClick={onCompareClick}
        >
          Compare
        </button>
      </div>
      <div className={classes.chipsRow}>
        {HEATMAP_SIGNALS.map((s) => (
          <button
            key={s.id}
            type="button"
            className={`${classes.chip} ${signal === s.id ? classes.chipActive : ""}`}
            onClick={() => onSignalChange(s.id)}
          >
            {s.label}
          </button>
        ))}
      </div>
      <div className={classes.filterDivider} />
      <div className={classes.focusBlock}>
        <div className={classes.focusTop}>
          <span className={classes.focusLabel}>Focus</span>
          <Text size="xs" c="teal" fw={600}>
            Advanced
          </Text>
        </div>
        <div className={classes.focusPills}>
          <button
            type="button"
            className={`${classes.pill} ${focusLens === "all" ? classes.pillActive : ""}`}
            onClick={() => onFocusLensChange("all")}
          >
            All interaction data
          </button>
          <button
            type="button"
            className={`${classes.pill} ${focusLens === "key" ? classes.pillActive : ""}`}
            onClick={() => onFocusLensChange("key")}
          >
            Key actions only
          </button>
        </div>
        <Text className={classes.focusHint}>
          All interaction data: density heatmap (glow / signal layers). Key
          actions: Pulse interaction regions as boxes; hover for per-interaction
          scores and average on each element.
        </Text>
      </div>
    </Box>
  );
}
