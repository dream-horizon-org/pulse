import React from "react";
import { Card, Badge, Stack, Box } from "@mantine/core";
import { MetricItem } from "./components/MetricItem";
import type { HeroMetricsProps } from "./HeroMetrics.interface";
import { mockMetricGroups } from "./__mock__/HeroMetrics.mock";
import classes from "./HeroMetrics.module.css";

/**
 * Hero Metrics Component
 * Displays metrics in structured grid layouts
 * Interaction Health: 2x2 grid
 * Network Health: 3+2 grid (3 items first row, 2 items second row)
 */
const HeroMetrics: React.FC<HeroMetricsProps> = ({ metricGroups }) => {
  // Use provided metric groups or fall back to mock data
  const groups = metricGroups ?? mockMetricGroups;

  const getGridClass = (groupTitle: string) => {
    if (groupTitle === "Interaction Health") {
      return classes.gridTwoByTwo;
    }
    if (groupTitle === "Network Health") {
      return classes.gridThreePlusTwo;
    }
    return classes.gridDefault;
  };

  return (
    <Card
      withBorder
      radius="md"
      padding="md"
      className={classes.heroMetricsContainer}
    >
      <Stack gap="md">
        {groups.map((group, groupIndex) => {
          const Icon = group.icon;
          return (
            <Box key={`group-${groupIndex}`} className={classes.metricSection}>
              {/* Section Header */}
              <Badge
                leftSection={Icon && <Icon size={12} />}
                variant="light"
                color={group.iconColor}
                size="sm"
                mb="xs"
                className={classes.sectionBadge}
              >
                {group.title}
              </Badge>

              {/* Metrics Grid */}
              <div className={getGridClass(group.title)}>
                {group.metrics.map((metric, metricIndex) => (
                  <MetricItem
                    key={`${metric.title}-${metricIndex}`}
                    {...metric}
                  />
                ))}
              </div>
            </Box>
          );
        })}
      </Stack>
    </Card>
  );
};

export default HeroMetrics;
