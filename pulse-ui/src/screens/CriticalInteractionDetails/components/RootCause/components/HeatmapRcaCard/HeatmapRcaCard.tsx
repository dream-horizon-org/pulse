import { Card, Group, Text } from "@mantine/core";
import { IconLayoutGrid, IconMap } from "@tabler/icons-react";
import type { HeatmapRcaCardProps } from "./HeatmapRcaCard.interface";
import classes from "./HeatmapRcaCard.module.css";

/**
 * Compact card linking to Screen → Heatmap for the RCA tab (same project/time range as interaction).
 */
export function HeatmapRcaCard({
  screenName,
  label,
  heatmapUrl,
}: HeatmapRcaCardProps) {
  return (
    <Card
      component="a"
      href={heatmapUrl}
      withBorder
      padding="lg"
      className={`${classes.card} ${classes.cardClickable}`}
      radius="md"
      aria-label={`Open heatmap for ${screenName}`}
    >
      <div className={classes.inner}>
        <div className={classes.titleRow}>
          <IconLayoutGrid
            size={18}
            color="var(--mantine-color-teal-7)"
            aria-hidden
          />
          <Text className={classes.screenName} component="span" lineClamp={2}>
            {screenName}
          </Text>
        </div>
        <div className={classes.labelBox}>
          <Text className={classes.labelText} lineClamp={4}>
            {label}
          </Text>
        </div>
        <div className={classes.footer}>
          <span className={classes.viewLink}>
            <Group gap="xs" wrap="nowrap" align="center">
              <IconMap size={14} aria-hidden />
              <span>View heatmap</span>
            </Group>
          </span>
        </div>
      </div>
    </Card>
  );
}
