import { Box, Text, Badge, Group } from "@mantine/core";
import { IconChevronRight } from "@tabler/icons-react";
import { NetworkApiCardProps } from "./NetworkApiCard.interface";
import classes from "./NetworkApiCard.module.css";

export const NetworkApiCard: React.FC<NetworkApiCardProps> = ({
  apiData,
  onClick,
}) => {
  const getMethodColor = (method: string) => {
    switch (method) {
      case "GET":
        return "blue";
      case "POST":
        return "green";
      case "PUT":
        return "orange";
      case "DELETE":
        return "red";
      default:
        return "gray";
    }
  };

  const getPerformanceIndicator = (avgResponseTime: number) => {
    // avgResponseTime is in nanoseconds, convert to ms for comparison
    const responseTimeMs = avgResponseTime / 1_000_000;
    if (responseTimeMs < 300) return { color: "green", label: "Fast" };
    if (responseTimeMs < 600) return { color: "orange", label: "Moderate" };
    return { color: "red", label: "Slow" };
  };

  const perfIndicator = getPerformanceIndicator(apiData.avgResponseTime);

  const responseTimeFormatter = (responseTime: number) => {
    // response time is in nanoseconds, so we need to convert it to milliseconds.
    const milliseconds = responseTime / 1000000;
    // if the response time is greater than 1000, then format it as seconds.
    if (milliseconds > 1000) {
      return `${(milliseconds / 1000).toFixed(1)}s`;
    }
    return `${milliseconds.toFixed(2)}ms`;
  };

  return (
    <Box className={classes.apiCard} onClick={onClick}>
      <Box className={classes.apiCardHeader}>
        <Group gap="xs" wrap="nowrap" style={{ flex: 1 }}>
          <Badge
            color={getMethodColor(apiData.method)}
            variant="light"
            size="sm"
            className={classes.methodBadge}
          >
            {apiData.method}
          </Badge>
          <Text size="sm" fw={500} className={classes.endpoint}>
            {apiData.endpoint}
          </Text>
        </Group>
        <IconChevronRight size={18} color="#0ba09a" />
      </Box>

      <Box className={classes.apiCardMetrics}>
        <Box className={classes.metricItem}>
          <Text size="xs" c="dimmed" className={classes.metricLabel}>
            Response Time
          </Text>
          <Group gap={6} wrap="nowrap">
            <Text size="sm" fw={600} c={perfIndicator.color}>
              {responseTimeFormatter(apiData.avgResponseTime)}
            </Text>
            <Badge
              size="xs"
              variant="dot"
              color={perfIndicator.color}
              className={classes.perfBadge}
            >
              {perfIndicator.label}
            </Badge>
          </Group>
        </Box>

        <Box className={classes.metricItem}>
          <Text size="xs" c="dimmed" className={classes.metricLabel}>
            Requests
          </Text>
          <Text size="sm" fw={600}>
            {apiData.requestCount.toLocaleString()}
          </Text>
        </Box>

        <Box className={classes.metricItem}>
          <Text size="xs" c="dimmed" className={classes.metricLabel}>
            Success Rate
          </Text>
          <Text
            size="sm"
            fw={600}
            c={apiData.successRate >= 98 ? "green" : "orange"}
          >
            {apiData.successRate}%
          </Text>
        </Box>

        <Box className={classes.metricItem}>
          <Text size="xs" c="dimmed" className={classes.metricLabel}>
            Error Rate
          </Text>
          <Text
            size="sm"
            fw={600}
            c={apiData.errorRate <= 2 ? "dimmed" : "red"}
          >
            {apiData.errorRate}%
          </Text>
        </Box>
      </Box>
    </Box>
  );
};
