import {
  Box,
  Paper,
  Stack,
  Text,
  Group,
  Badge,
  ThemeIcon,
} from "@mantine/core";
import {
  IconBulb,
  IconAlertTriangle,
  IconDatabase,
  IconCalendar,
} from "@tabler/icons-react";
import { AiInsightsProps } from "./AiInsights.interface";
import classes from "./AiInsights.module.css";

/**
 * @deprecated Use ResponseDetail in AiChat instead.
 * Kept for backwards compatibility but no longer rendered in AI mode.
 */
export function AiInsights({
  insights,
  sourcesAnalyzed,
  timeRange,
}: AiInsightsProps) {
  const formatDate = (isoString: string): string => {
    try {
      const date = new Date(isoString);
      return date.toLocaleString("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        timeZoneName: "short",
      });
    } catch {
      return isoString;
    }
  };

  return (
    <Paper className={classes.container} withBorder>
      <Stack gap="md" p="md">
        {/* Answer */}
        <Box className={classes.summaryCard}>
          <Group gap="xs" mb="xs">
            <ThemeIcon size="sm" color="teal" variant="light">
              <IconBulb size={16} />
            </ThemeIcon>
            <Text size="sm" fw={600} c="dimmed" tt="uppercase">
              Answer
            </Text>
          </Group>
          <Text className={classes.summaryText}>{insights.answer}</Text>
        </Box>

        {/* Key Points */}
        {insights.keyPoints.length > 0 && (
          <Box className={classes.section}>
            <Group gap="xs" className={classes.sectionTitle}>
              <IconAlertTriangle size={16} color="var(--mantine-color-orange-6)" />
              <Text>Key Findings</Text>
            </Group>
            <ul className={classes.findingsList}>
              {insights.keyPoints.map((point, index: number) => (
                <li key={index} className={classes.findingItem}>
                  <IconAlertTriangle
                    size={16}
                    className={classes.findingIcon}
                  />
                  <Text className={classes.findingText}>{point.text}</Text>
                </li>
              ))}
            </ul>
          </Box>
        )}

        {/* Data Sources */}
        {(sourcesAnalyzed || timeRange) && (
          <Box className={classes.sourcesSection}>
            <Group gap="xs" mb="md">
              <IconDatabase size={16} color="var(--mantine-color-teal-6)" />
              <Text size="sm" fw={600} c="dimmed" tt="uppercase">
                Data Sources
              </Text>
            </Group>

            {sourcesAnalyzed && sourcesAnalyzed.length > 0 && (
              <Box mb="md">
                <Text size="xs" c="dimmed" mb="xs">
                  Tables/Databases:
                </Text>
                <Box className={classes.sourcesList}>
                  {sourcesAnalyzed.map((source: string, index: number) => (
                    <Badge
                      key={index}
                      variant="light"
                      color="teal"
                      size="sm"
                      className={classes.sourceBadge}
                    >
                      {source}
                    </Badge>
                  ))}
                </Box>
              </Box>
            )}

            {timeRange && (
              <Box className={classes.partitionInfo}>
                <Group gap="xs" mb="xs">
                  <IconCalendar size={14} color="var(--mantine-color-gray-6)" />
                  <Text size="xs" fw={600} c="dimmed">
                    Time Range
                  </Text>
                </Group>
                <Box className={classes.partitionRow}>
                  <Text className={classes.partitionLabel}>Start:</Text>
                  <Text className={classes.partitionValue}>
                    {formatDate(timeRange.start)}
                  </Text>
                </Box>
                <Box className={classes.partitionRow}>
                  <Text className={classes.partitionLabel}>End:</Text>
                  <Text className={classes.partitionValue}>
                    {formatDate(timeRange.end)}
                  </Text>
                </Box>
              </Box>
            )}
          </Box>
        )}
      </Stack>
    </Paper>
  );
}
