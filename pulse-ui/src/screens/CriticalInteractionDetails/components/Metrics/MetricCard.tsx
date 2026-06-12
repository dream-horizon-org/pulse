import React from "react";
import { Card, Group, Stack, Text, ThemeIcon, rem } from "@mantine/core";
import { IconTrendingUp, IconTrendingDown } from "@tabler/icons-react";
import { MetricCardProps } from "./MetricCard.interface";

/**
 * MetricCard Component (Mantine version)
 * Reusable card for displaying a single metric with optional trend indicator
 */
const MetricCard: React.FC<MetricCardProps> = ({
  icon,
  label,
  value,
  unit = "",
  subtitle,
  trend,
  trendDirection, // 'up' | 'down' | 'neutral'
  color = "blue",
  backgroundColor,
  size = "medium", // 'small' | 'medium' | 'large'
  onClick,
}) => {
  // Size configurations
  const sizeConfig = {
    small: {
      iconSize: 40,
      valueSize: "lg",
      labelSize: "xs",
    },
    medium: {
      iconSize: 50,
      valueSize: "xl",
      labelSize: "xs",
    },
    large: {
      iconSize: 60,
      valueSize: "2xl",
      labelSize: "sm",
    },
  };

  const config = sizeConfig[size];

  const getTrendIcon = () => {
    if (trendDirection === "up") return <IconTrendingUp size={14} />;
    if (trendDirection === "down") return <IconTrendingDown size={14} />;
    return null;
  };

  const getTrendColor = () => {
    if (trendDirection === "up") return "teal";
    if (trendDirection === "down") return "red";
    return "dimmed";
  };
  return (
    <Card
      withBorder
      radius="md"
      padding="md"
      onClick={onClick}
      style={{
        height: "100%",
        cursor: onClick ? "pointer" : "default",
        backgroundColor: backgroundColor,
        transition: "box-shadow 0.2s ease",
      }}
      shadow={onClick ? "sm" : undefined}
      onMouseEnter={(e) => {
        if (onClick)
          e.currentTarget.style.boxShadow = "0 2px 10px rgba(0,0,0,0.1)";
      }}
      onMouseLeave={(e) => {
        if (onClick) e.currentTarget.style.boxShadow = "";
      }}
    >
      <Stack gap="xs">
        {icon ? (
          <Group align="center" gap="md">
            {/* Icon container */}
            <ThemeIcon
              color={color}
              variant="light"
              size={rem(config.iconSize)}
              radius="md"
              style={{
                flexShrink: 0,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              {icon}
            </ThemeIcon>

            <Stack gap={0} style={{ flex: 1 }}>
              <Text size={config.labelSize} c="dimmed" fw={600}>
                {label}
              </Text>

              <Text size={config.valueSize} fw={700} c={color}>
                {value}
                {unit && (
                  <Text span size="sm" fw={400} ml={4}>
                    {unit}
                  </Text>
                )}
              </Text>
            </Stack>
          </Group>
        ) : (
          <Stack gap={4}>
            <Text size={config.labelSize} c="dimmed" fw={600}>
              {label}
            </Text>

            <Text size={config.valueSize} fw={700} c={color}>
              {value}
              {unit && (
                <Text span size="sm" fw={400} ml={4}>
                  {unit}
                </Text>
              )}
            </Text>
          </Stack>
        )}

        {subtitle && (
          <Text size="xs" c="dimmed" mt={4}>
            {subtitle}
          </Text>
        )}

        {trend && (
          <Group gap={4} mt={4}>
            {getTrendIcon()}
            <Text size="xs" fw={600} c={getTrendColor()}>
              {trend}
            </Text>
          </Group>
        )}
      </Stack>
    </Card>
  );
};

export default MetricCard;
