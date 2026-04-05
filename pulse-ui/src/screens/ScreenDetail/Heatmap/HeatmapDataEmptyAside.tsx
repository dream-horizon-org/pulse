import { Stack, Text } from "@mantine/core";
import { IconMapPinOff } from "@tabler/icons-react";
import {
  HEATMAP_COPY_EMPTY_TITLE,
  heatmapCopyEmptyBody,
} from "./heatmapCopy";
import { userFacingHeatmapScreenLabel } from "./heatmapEmptyState";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapDataEmptyAsideProps {
  screenName?: string;
  contextScreenName?: string;
}

/**
 * Right-rail copy when the API returned successfully but there are no tap/frustration bins.
 */
export function HeatmapDataEmptyAside({
  screenName,
  contextScreenName,
}: HeatmapDataEmptyAsideProps) {
  const labelForCopy = userFacingHeatmapScreenLabel(
    screenName,
    contextScreenName,
  );

  return (
    <div className={classes.heatmapDataEmptyAside}>
      <Stack align="center" gap="sm" maw={380}>
        <IconMapPinOff size={28} stroke={1.25} color="var(--mantine-color-gray-5)" />
        <Text size="sm" fw={600} ta="center">
          {HEATMAP_COPY_EMPTY_TITLE}
        </Text>
        <Text size="xs" c="dimmed" ta="center" lh={1.5}>
          {heatmapCopyEmptyBody(labelForCopy)}
        </Text>
      </Stack>
    </div>
  );
}
