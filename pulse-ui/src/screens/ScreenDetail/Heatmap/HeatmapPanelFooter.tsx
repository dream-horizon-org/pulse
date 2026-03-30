import { Link } from "react-router-dom";
import { Anchor, Box, Group, Text } from "@mantine/core";
import type { HeatmapPanelProps } from "./heatmapPanel.types";
import { formatAvgTime, formatInt } from "./heatmapPanelUtils";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapDrillDownProps {
  userEngagementPath: string;
  appVitalsPath: string;
}

export function HeatmapDrillDown({
  userEngagementPath,
  appVitalsPath,
}: HeatmapDrillDownProps) {
  return (
    <Box className={classes.crossLink}>
      <Text size="sm" fw={600} mb={6}>
        Drill down
      </Text>
      <Group gap="lg">
        <Anchor component={Link} to={userEngagementPath} size="sm">
          User engagement trends
        </Anchor>
        <Anchor component={Link} to={appVitalsPath} size="sm">
          Crashes &amp; ANRs
        </Anchor>
      </Group>
    </Box>
  );
}

export interface HeatmapEngagementCardsProps {
  engagement: HeatmapPanelProps["engagement"];
}

export function HeatmapEngagementCards({
  engagement,
}: HeatmapEngagementCardsProps) {
  return (
    <div className={classes.engagementSection}>
      <div className={classes.engagementTitle}>
        <div className={classes.engagementTitleBar} />
        <span className={classes.engagementTitleText}>
          Engagement metrics
        </span>
      </div>
      <div className={classes.engagementGrid}>
        <div className={classes.engagementCard}>
          <div className={classes.engagementCardLabel}>Avg time spent</div>
          <div className={classes.engagementCardValue}>
            {formatAvgTime(engagement?.avgTimeSpent ?? null)}
          </div>
        </div>
        <div className={classes.engagementCard}>
          <div className={classes.engagementCardLabel}>Sessions (range)</div>
          <div className={classes.engagementCardValue}>
            {formatInt(engagement?.totalSessions ?? 0)}
          </div>
        </div>
        <div className={classes.engagementCard}>
          <div className={classes.engagementCardLabel}>Unique users</div>
          <div className={classes.engagementCardValue}>
            {formatInt(engagement?.totalUsers ?? 0)}
          </div>
        </div>
      </div>
    </div>
  );
}
