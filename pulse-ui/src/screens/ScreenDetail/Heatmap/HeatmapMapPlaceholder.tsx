import { Group, Loader, Text } from "@mantine/core";
import { HEATMAP_COPY_LOADING_HEATMAP } from "./heatmapCopy";
import classes from "./HeatmapPanel.module.css";

/**
 * Loading state inside the compare map column phone-frame area.
 */
export function HeatmapMapPlaceholder() {
  return (
    <div className={classes.heatmapMapPlaceholder}>
      <Group justify="center" align="center" gap="sm" py="xl" w="100%" wrap="nowrap">
        <Loader size="sm" color="teal" />
        <Text size="sm" c="dimmed">
          {HEATMAP_COPY_LOADING_HEATMAP}
        </Text>
      </Group>
    </div>
  );
}
