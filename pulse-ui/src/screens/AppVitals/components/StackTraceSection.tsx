import { useState, useCallback } from "react";
import {
  Box,
  Text,
  Paper,
  Badge,
  Group,
  ActionIcon,
  Tabs,
  Tooltip,
  CopyButton,
} from "@mantine/core";
import {
  IconChevronLeft,
  IconChevronRight,
  IconCode,
  IconTimeline,
  IconCopy,
  IconCheck,
} from "@tabler/icons-react";
import { useOccurrenceBreadcrumbs } from "../pages/hooks/useOccurrenceBreadcrumbs";
import { BreadcrumbTimeline } from "./BreadcrumbTimeline";
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
  sessionId?: string;
  sdkVersion?: string;
  appVersionCode?: string;
  networkProvider?: string;
  userId?: string;
  interactions?: string[];
  bundleId?: string;
}

interface StackTraceSectionProps {
  stackTraces: StackTrace[];
}

export const StackTraceSection: React.FC<StackTraceSectionProps> = ({
  stackTraces = [],
}) => {
  const [currentOccurrence, setCurrentOccurrence] = useState(0);
  const [activeTab, setActiveTab] = useState<string | null>("stacktrace");
  const [hasOpenedBreadcrumbs, setHasOpenedBreadcrumbs] = useState(false);

  const safeCurrentOccurrence = Math.min(
    currentOccurrence,
    Math.max(0, stackTraces.length - 1),
  );

  const currentTrace =
    stackTraces.length > 0 ? stackTraces[safeCurrentOccurrence] : null;

  const {
    breadcrumbs,
    queryState: breadcrumbsQueryState,
    resetForNewOccurrence,
  } = useOccurrenceBreadcrumbs({
    sessionId: currentTrace?.sessionId || "",
    errorTimestamp: currentTrace?.timestamp || null,
    enabled:
      hasOpenedBreadcrumbs &&
      activeTab === "breadcrumbs" &&
      !!currentTrace?.sessionId,
  });

  const handleTabChange = useCallback((value: string | null) => {
    setActiveTab(value);
    if (value === "breadcrumbs") setHasOpenedBreadcrumbs(true);
  }, []);

  const handlePreviousOccurrence = () => {
    if (stackTraces.length === 0) return;
    setCurrentOccurrence((prev) =>
      prev > 0 ? prev - 1 : stackTraces.length - 1,
    );
    resetForNewOccurrence();
  };

  const handleNextOccurrence = () => {
    if (stackTraces.length === 0) return;
    setCurrentOccurrence((prev) =>
      prev < stackTraces.length - 1 ? prev + 1 : 0,
    );
    resetForNewOccurrence();
  };

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

      {currentTrace?.title && (
        <Paper className={classes.titleHeader}>
          <Group justify="space-between" align="flex-start">
            <Box style={{ flex: 1 }}>
              <Text className={classes.errorTitle}>{currentTrace.title}</Text>
              {currentTrace?.errorMessage &&
                currentTrace.errorMessage !== currentTrace.title && (
                  <Text className={classes.errorMessage} lineClamp={2}>
                    {currentTrace.errorMessage}
                  </Text>
                )}
            </Box>
            {currentTrace?.errorType && (
              <Badge
                size="sm"
                variant="light"
                color="red"
                className={classes.errorTypeBadge}
              >
                {currentTrace.errorType}
              </Badge>
            )}
          </Group>
        </Paper>
      )}

      <Paper className={classes.compactHeader}>
        <Group justify="space-between" align="center" wrap="wrap" gap="sm">
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
                {currentTrace?.appVersionCode &&
                  ` (${currentTrace.appVersionCode})`}
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
          <Badge size="md" variant="outline" color="gray">
            {currentTrace?.timestamp
              ? currentTrace.timestamp.toLocaleString()
              : "Unknown Time"}
          </Badge>
        </Group>
      </Paper>

      {(currentTrace?.sdkVersion ||
        currentTrace?.networkProvider ||
        currentTrace?.sessionId ||
        currentTrace?.bundleId ||
        currentTrace?.interactions?.length) && (
        <Paper className={classes.detailsRow}>
          <Group gap="lg" wrap="wrap">
            {currentTrace.sdkVersion && (
              <Group gap={6}>
                <Text className={classes.infoLabel}>SDK:</Text>
                <Text className={classes.infoValueMono}>
                  {currentTrace.sdkVersion}
                </Text>
              </Group>
            )}
            {currentTrace.networkProvider && (
              <Group gap={6}>
                <Text className={classes.infoLabel}>Network:</Text>
                <Text className={classes.infoValue}>
                  {currentTrace.networkProvider}
                </Text>
              </Group>
            )}
            {currentTrace.bundleId && (
              <Group gap={6}>
                <Text className={classes.infoLabel}>Bundle:</Text>
                <Text className={classes.infoValueMono}>
                  {currentTrace.bundleId}
                </Text>
              </Group>
            )}
            {currentTrace.sessionId && (
              <Group gap={6}>
                <Text className={classes.infoLabel}>Session:</Text>
                <Tooltip label={currentTrace.sessionId} position="top">
                  <Text className={classes.infoValueMono}>
                    {currentTrace.sessionId.length > 12
                      ? `${currentTrace.sessionId.slice(0, 12)}…`
                      : currentTrace.sessionId}
                  </Text>
                </Tooltip>
                <CopyButton value={currentTrace.sessionId}>
                  {({ copied, copy }) => (
                    <ActionIcon
                      size="xs"
                      variant="subtle"
                      color={copied ? "teal" : "gray"}
                      onClick={copy}
                    >
                      {copied ? (
                        <IconCheck size={12} />
                      ) : (
                        <IconCopy size={12} />
                      )}
                    </ActionIcon>
                  )}
                </CopyButton>
              </Group>
            )}
            {currentTrace.interactions &&
              currentTrace.interactions.length > 0 && (
                <Group gap={6}>
                  <Text className={classes.infoLabel}>Interactions:</Text>
                  {currentTrace.interactions.map((interaction) => (
                    <Badge
                      key={interaction}
                      size="xs"
                      variant="light"
                      color="cyan"
                    >
                      {interaction}
                    </Badge>
                  ))}
                </Group>
              )}
          </Group>
        </Paper>
      )}

      <Tabs
        value={activeTab}
        onChange={handleTabChange}
        className={classes.tabs}
      >
        <Tabs.List className={classes.tabsList}>
          <Tabs.Tab
            value="stacktrace"
            leftSection={<IconCode size={14} />}
            className={classes.tab}
          >
            Stack Trace
          </Tabs.Tab>
          <Tabs.Tab
            value="breadcrumbs"
            leftSection={<IconTimeline size={14} />}
            className={classes.tab}
          >
            Breadcrumbs
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="stacktrace">
          <Paper className={classes.traceContainer}>
            <pre className={classes.tracePre}>
              {currentTrace?.trace || "No stack trace available"}
            </pre>
          </Paper>
        </Tabs.Panel>

        <Tabs.Panel value="breadcrumbs">
          <BreadcrumbTimeline
            breadcrumbs={breadcrumbs}
            isLoading={breadcrumbsQueryState.isLoading}
            isError={breadcrumbsQueryState.isError}
            errorMessage={breadcrumbsQueryState.errorMessage}
          />
        </Tabs.Panel>
      </Tabs>
    </Paper>
  );
};
