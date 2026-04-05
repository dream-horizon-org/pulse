import { Stack, Text } from "@mantine/core";
import { IconMapPinOff } from "@tabler/icons-react";
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
          No heatmap data for this screen
        </Text>
        <Text size="xs" c="dimmed" ta="center" lh={1.5}>
          {labelForCopy
            ? `We didn’t find any taps or frustration in this range for “${labelForCopy}”. Try a wider time range or different filters.`
            : "We didn’t find any taps or frustration in this range. Try a wider time range or different filters."}
        </Text>
      </Stack>
    </div>
  );
}
