import { Center, Loader, Stack, Text } from "@mantine/core";
import { IconPhotoOff } from "@tabler/icons-react";
import { HEATMAP_SCREEN_FALLBACK_URL } from "./heatmapViz.constants";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapScreenUnderlayProps {
  screenshotUrl: string | null | undefined;
  /** True while JSON/base64 capture manifests are being fetched and decoded. */
  loading?: boolean;
}

/**
 * Screen capture under the heatmap. When there is no URL (API omitted screenshots),
 * show a neutral plate plus copy so heatmap-only mode is obvious.
 */
export function HeatmapScreenUnderlay({
  screenshotUrl,
  loading = false,
}: HeatmapScreenUnderlayProps) {
  if (loading && !screenshotUrl) {
    return (
      <div className={classes.screenUnderlayEmpty}>
        <Center h="100%" py="xl">
          <Loader size="sm" type="dots" />
        </Center>
      </div>
    );
  }

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
    <div className={classes.screenUnderlayEmpty}>
      <Stack align="center" justify="center" gap={6} px="md">
        <IconPhotoOff size={28} stroke={1.2} color="var(--mantine-color-gray-5)" />
        <Text size="xs" c="dimmed" ta="center" fw={500}>
          No screenshot
        </Text>
        <Text size="xs" c="dimmed" ta="center" maw={200} lh={1.4} opacity={0.85}>
          Heatmap still reflects taps and signals on this layout.
        </Text>
      </Stack>
    </div>
  );
}
