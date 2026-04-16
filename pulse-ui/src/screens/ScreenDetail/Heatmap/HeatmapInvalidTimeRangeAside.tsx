import { Stack, Text } from "@mantine/core";
import { IconCalendarOff } from "@tabler/icons-react";
import {
  HEATMAP_COPY_INVALID_TIME_BODY,
  HEATMAP_COPY_INVALID_TIME_TITLE,
} from "./heatmapCopy";
import classes from "./HeatmapPanel.module.css";

/**
 * Shown when From/To are missing or invalid in the heatmap time filter (custom range).
 */
export function HeatmapInvalidTimeRangeAside() {
  return (
    <div className={classes.heatmapDataEmptyAside}>
      <Stack align="center" gap="sm" maw={380}>
        <IconCalendarOff
          size={28}
          stroke={1.25}
          color="var(--mantine-color-gray-5)"
        />
        <Text size="sm" fw={600} ta="center">
          {HEATMAP_COPY_INVALID_TIME_TITLE}
        </Text>
        <Text size="xs" c="dimmed" ta="center" lh={1.5}>
          {HEATMAP_COPY_INVALID_TIME_BODY}
        </Text>
      </Stack>
    </div>
  );
}
