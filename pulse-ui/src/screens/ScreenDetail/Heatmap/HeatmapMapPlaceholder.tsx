import { Alert, Group, Loader, Stack, Text } from "@mantine/core";
import { IconMapPinOff } from "@tabler/icons-react";
import { userFacingHeatmapScreenLabel } from "./heatmapEmptyState";
import classes from "./HeatmapPanel.module.css";

export type HeatmapMapPlaceholderVariant = "empty" | "loading";

export interface HeatmapMapPlaceholderProps {
  variant: HeatmapMapPlaceholderVariant;
  /** Often `metadata.screenName` (may be a dev/mock sentinel). */
  screenName?: string;
  /** UI context when the API name should stay hidden — e.g. route screen or compare picker. */
  contextScreenName?: string;
}

/**
 * Non-functional states inside the phone-frame area — empty heatmap or loading column.
 */
export function HeatmapMapPlaceholder({
  variant,
  screenName,
  contextScreenName,
}: HeatmapMapPlaceholderProps) {
  if (variant === "loading") {
    return (
      <div className={classes.heatmapMapPlaceholder}>
        <Group justify="center" align="center" gap="sm" py="xl" w="100%" wrap="nowrap">
          <Loader size="sm" color="teal" />
          <Text size="sm" c="dimmed">
            Loading heatmap…
          </Text>
        </Group>
      </div>
    );
  }

  const labelForCopy = userFacingHeatmapScreenLabel(
    screenName,
    contextScreenName,
  );

  return (
    <div className={classes.heatmapMapPlaceholder}>
      <Stack align="center" gap="sm" py="xl" px="md">
        <IconMapPinOff size={36} stroke={1.25} color="var(--mantine-color-gray-5)" />
        <Text size="sm" fw={600} ta="center">
          No heatmap data for this screen
        </Text>
        <Text size="xs" c="dimmed" ta="center" maw={320} lh={1.5}>
          {labelForCopy
            ? `We didn’t find any taps or frustration in this range for “${labelForCopy}”. Try a wider time range or different filters.`
            : "We didn’t find any taps or frustration in this range. Try a wider time range or different filters."}
        </Text>
      </Stack>
    </div>
  );
}

export interface HeatmapInlineErrorProps {
  message: string;
  title?: string;
}

export function HeatmapInlineError({
  message,
  title = "Couldn’t load heatmap",
}: HeatmapInlineErrorProps) {
  return (
    <div className={classes.heatmapMapPlaceholder}>
      <Alert color="red" title={title} mt={0}>
        {message}
      </Alert>
    </div>
  );
}
