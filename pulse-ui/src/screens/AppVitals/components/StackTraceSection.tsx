import { useState } from "react";
import {
  Box,
  Text,
  Paper,
  Badge,
  Group,
  ActionIcon,
} from "@mantine/core";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import classes from "./StackTraceSection.module.css";

interface StackTrace {
  timestamp: Date;
  device: string;
  osVersion: string;
  appVersion: string;
  trace: string;
  title?: string;
  screenName?: string;
  platform?: string;
  errorMessage?: string;
  errorType?: string;
}

interface StackTraceSectionProps {
  stackTraces: StackTrace[];
}

export const StackTraceSection: React.FC<StackTraceSectionProps> = ({
  stackTraces = [],
}) => {
  const [currentOccurrence, setCurrentOccurrence] = useState(0);

  // Ensure currentOccurrence is within bounds
  const safeCurrentOccurrence = Math.min(
    currentOccurrence,
    Math.max(0, stackTraces.length - 1),
  );

  const handlePreviousOccurrence = () => {
    if (stackTraces.length === 0) return;
    setCurrentOccurrence((prev) =>
      prev > 0 ? prev - 1 : stackTraces.length - 1,
    );
  };

  const handleNextOccurrence = () => {
    if (stackTraces.length === 0) return;
    setCurrentOccurrence((prev) =>
      prev < stackTraces.length - 1 ? prev + 1 : 0,
    );
  };

  // Show empty state if no stack traces
  if (!stackTraces || stackTraces.length === 0) {
    return (
      <Paper className={classes.sectionContainer}>
        <Box className={classes.header}>
          <Text className={classes.sectionTitle}>Error Trace</Text>
        </Box>
        <Paper className={classes.traceContainer}>
          <Text c="dimmed" ta="center" py="xl">
            No stack traces available for this issue.
          </Text>
        </Paper>
      </Paper>
    );
  }

  const currentTrace = stackTraces[safeCurrentOccurrence];

  return (
    <Paper className={classes.sectionContainer}>
      <Box className={classes.header}>
        <Text className={classes.sectionTitle}>Error Trace</Text>
        <Group gap="sm">
          <ActionIcon
            variant="light"
            color="teal"
            onClick={handlePreviousOccurrence}
            className={classes.navButton}
            disabled={stackTraces.length === 0}
          >
            <IconChevronLeft size={18} />
          </ActionIcon>
          <Text className={classes.occurrenceLabel}>
            Occurrence {safeCurrentOccurrence + 1} of {stackTraces.length}
          </Text>
          <ActionIcon
            variant="light"
            color="teal"
            onClick={handleNextOccurrence}
            className={classes.navButton}
            disabled={stackTraces.length === 0}
          >
            <IconChevronRight size={18} />
          </ActionIcon>
        </Group>
      </Box>

      {/* Title/Error Type Header */}
      {currentTrace?.title && (
        <Paper className={classes.titleHeader}>
          <Text className={classes.errorTitle}>{currentTrace.title}</Text>
          {currentTrace?.errorMessage && currentTrace.errorMessage !== currentTrace.title && (
            <Text className={classes.errorMessage} lineClamp={2}>
              {currentTrace.errorMessage}
            </Text>
          )}
        </Paper>
      )}

      {/* Compact Header with Device Info and Actions */}
      <Paper className={classes.compactHeader}>
        <Group justify="space-between" align="center" wrap="wrap" gap="sm">
          {/* Left: Device Info */}
          <Group gap="lg" wrap="wrap">
            {currentTrace?.platform && (
              <Group gap={6}>
                <Text className={classes.infoLabel}>Platform:</Text>
                <Text className={classes.infoValue}>
                  {currentTrace.platform}
                </Text>
              </Group>
            )}
            <Group gap={6}>
              <Text className={classes.infoLabel}>Device:</Text>
              <Text className={classes.infoValue}>
                {currentTrace?.device || "Unknown Device"}
              </Text>
            </Group>
            <Group gap={6}>
              <Text className={classes.infoLabel}>OS:</Text>
              <Text className={classes.infoValue}>
                {currentTrace?.osVersion || "Unknown OS"}
              </Text>
            </Group>
            <Group gap={6}>
              <Text className={classes.infoLabel}>Version:</Text>
              <Text className={classes.infoValueMono}>
                {currentTrace?.appVersion || "Unknown Version"}
              </Text>
            </Group>
            {currentTrace?.screenName && (
              <Group gap={6}>
                <Text className={classes.infoLabel}>Screen:</Text>
                <Text className={classes.infoValue}>
                  {currentTrace.screenName}
                </Text>
              </Group>
            )}
          </Group>

          {/* Right: Timestamp */}
          <Badge size="md" variant="outline" color="gray">
            {currentTrace?.timestamp
              ? currentTrace.timestamp.toLocaleString()
              : "Unknown Time"}
          </Badge>
        </Group>
      </Paper>

      {/* Stack Trace Display */}
      <Paper className={classes.traceContainer}>
        <pre className={classes.tracePre}>
          {currentTrace?.trace || "No stack trace available"}
        </pre>
      </Paper>
    </Paper>
  );
};
