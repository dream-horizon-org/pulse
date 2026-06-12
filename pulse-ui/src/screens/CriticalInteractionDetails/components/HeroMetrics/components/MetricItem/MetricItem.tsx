import React from "react";
import { Group, Text, ThemeIcon, Stack } from "@mantine/core";
import { MetricItemProps } from "./MetricItem.interface";
import classes from "./MetricItem.module.css";

export function MetricItem({
  title,
  value,
  trend,
  color,
  icon: Icon,
  onClick,
}: MetricItemProps) {
  const getTrendColor = () => {
    if (trend.startsWith("+")) return "red";
    if (trend.startsWith("-")) return "teal";
    return "dimmed";
  };

  return (
    <div
      className={classes.metricItem}
      onClick={onClick}
      style={{ cursor: onClick ? "pointer" : "default" }}
    >
      <Group gap={8} wrap="nowrap" align="flex-start">
        {/* Icon - subtle and single color */}
        <ThemeIcon
          color="gray"
          variant="subtle"
          size="sm"
          radius="md"
          className={classes.subtleIcon}
        >
          <Icon size={14} />
        </ThemeIcon>

        {/* Content */}
        <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
          <Group gap={6} align="baseline" wrap="nowrap">
            <Text fw={700} size="md" c="dark">
              {value}
            </Text>
            <Text size="xs" fw={600} c={getTrendColor()}>
              {trend}
            </Text>
          </Group>
          <Text c="dimmed" size="xs" fw={500}>
            {title}
          </Text>
        </Stack>
      </Group>
    </div>
  );
}
