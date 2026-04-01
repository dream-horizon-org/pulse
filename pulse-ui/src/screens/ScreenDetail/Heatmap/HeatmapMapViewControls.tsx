import { Divider, Group, Text } from "@mantine/core";
import { HEATMAP_SIGNALS } from "./heatmapPanelUtils";
import type { HeatmapMapViewControlsProps } from "./heatmapFilterPanel.types";
import heatmapClasses from "./HeatmapPanel.module.css";

export function HeatmapMapViewControls({
  signal,
  onSignalChange,
  focusLens,
  onFocusLensChange,
}: HeatmapMapViewControlsProps) {
  const labelProps = {
    size: "xs" as const,
    fw: 600 as const,
    c: "#0ba09a",
    className: heatmapClasses.controlsLabel,
  };

  return (
    <Group
      gap="sm"
      align="center"
      wrap="wrap"
      className={heatmapClasses.heatmapToolbarMapControls}
    >
      <Text {...labelProps}>Map type</Text>
      <div className={heatmapClasses.focusPills}>
        <button
          type="button"
          className={`${heatmapClasses.pill} ${focusLens === "all" ? heatmapClasses.pillActive : ""}`}
          onClick={() => onFocusLensChange?.("all")}
        >
          Heat map
        </button>
        <button
          type="button"
          className={`${heatmapClasses.pill} ${focusLens === "key" ? heatmapClasses.pillActive : ""}`}
          onClick={() => onFocusLensChange?.("key")}
        >
          Interaction map
        </button>
      </div>
      {focusLens === "all" ? (
        <>
          <Divider orientation="vertical" h={24} />
          <Text {...labelProps}>Layer</Text>
          <div className={heatmapClasses.chipsRow}>
            {HEATMAP_SIGNALS.map((s) => (
              <button
                key={s.id}
                type="button"
                className={`${heatmapClasses.chip} ${signal === s.id ? heatmapClasses.chipActive : ""}`}
                onClick={() => onSignalChange?.(s.id)}
              >
                {s.label}
              </button>
            ))}
          </div>
        </>
      ) : null}
    </Group>
  );
}
