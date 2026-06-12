import React from "react";
import { Stack, Text, ThemeIcon, Group } from "@mantine/core";
import { MetricItem } from "../MetricItem";
import { MetricGroupProps } from "./MetricGroup.interface";
import classes from "./MetricGroup.module.css";

export function MetricGroup({
  title,
  metrics,
  icon: Icon,
  iconColor = "blue",
}: MetricGroupProps) {
  return (
    <Stack gap="xs" className={classes.metricGroup}>
      {/* Section Header */}
      <Group gap={6}>
        {Icon && (
          <ThemeIcon color={iconColor} variant="light" size="sm" radius="sm">
            <Icon size={14} />
          </ThemeIcon>
        )}
        <Text fw={600} size="xs" tt="uppercase" c="dimmed">
          {title}
        </Text>
      </Group>

      {/* Metrics Stack */}
      <Stack gap="xs">
        {metrics.map((metric, index) => (
          <MetricItem key={`${metric.title}-${index}`} {...metric} />
        ))}
      </Stack>
    </Stack>
  );
}
