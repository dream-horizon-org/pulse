/**
 * QueryHistory Component
 * Modal to display user's query history with copy and reuse functionality
 */

import {
  Modal,
  Box,
  Stack,
  Group,
  Text,
  Badge,
  ActionIcon,
  Tooltip,
  ScrollArea,
  TextInput,
  Center,
  Alert,
  Code,
  CopyButton,
  Button,
  Divider,
  ThemeIcon,
  Paper,
  Skeleton,
} from "@mantine/core";
import {
  IconHistory,
  IconSearch,
  IconCopy,
  IconCheck,
  IconPlayerPlay,
  IconClock,
  IconDatabase,
  IconAlertCircle,
  IconCircleCheck,
  IconCircleX,
  IconRefresh,
  IconLoader,
  IconBan,
} from "@tabler/icons-react";
import { useState, useMemo } from "react";
import { useGetQueryHistory, QueryHistoryItem } from "../../../../hooks";

import classes from "./QueryHistory.module.css";

interface QueryHistoryProps {
  opened: boolean;
  onClose: () => void;
  onSelectQuery: (query: string) => void;
}

// Format date to readable string (accepts timestamp in milliseconds)
const formatDate = (timestamp: number | undefined | null): string => {
  if (!timestamp) return "—";
  
  const date = new Date(timestamp);
  if (isNaN(date.getTime())) return "—";
  
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return "Just now";
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
};

// Format bytes to readable string
const formatBytes = (bytes?: number): string => {
  if (!bytes) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
};

// Format duration
const formatDuration = (ms?: number): string => {
  if (!ms) return "—";
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  return `${(ms / 60000).toFixed(1)}m`;
};

// Get status badge color and icon
const getStatusConfig = (status: QueryHistoryItem["status"] | string | undefined) => {
  switch (status) {
    case "COMPLETED":
      return { color: "teal", icon: IconCircleCheck, label: "Completed" };
    case "FAILED":
      return { color: "red", icon: IconCircleX, label: "Failed" };
    case "CANCELLED":
      return { color: "orange", icon: IconBan, label: "Cancelled" };
    case "RUNNING":
      return { color: "blue", icon: IconLoader, label: "Running" };
    case "SUBMITTED":
      return { color: "gray", icon: IconClock, label: "Submitted" };
    default:
      return { color: "gray", icon: IconClock, label: status || "Unknown" };
  }
};

// Truncate SQL for preview
const truncateSql = (sql: string | undefined | null, maxLength: number = 150): string => {
  if (!sql) return "—";
  if (sql.length <= maxLength) return sql;
  return sql.substring(0, maxLength) + "...";
};

export function QueryHistory({ opened, onClose, onSelectQuery }: QueryHistoryProps) {
  const [searchQuery, setSearchQuery] = useState("");

  // Fetch query history
  const {
    data: historyResponse,
    isLoading,
    error,
    refetch,
  } = useGetQueryHistory({ enabled: opened });

  // Filter queries based on search
  const filteredQueries = useMemo(() => {
    const queries = historyResponse?.data?.queries || [];
    if (!searchQuery.trim()) return queries;

    const search = searchQuery.toLowerCase();
    return queries.filter((q) =>
      (q.queryString?.toLowerCase() || "").includes(search) ||
      (q.status?.toLowerCase() || "").includes(search)
    );
  }, [historyResponse?.data?.queries, searchQuery]);

  // Handle query selection
  const handleSelectQuery = (queryString: string) => {
    onSelectQuery(queryString);
    onClose();
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={
        <Group gap="sm">
          <ThemeIcon size="md" variant="light" color="teal" radius="md">
            <IconHistory size={16} />
          </ThemeIcon>
          <Text fw={600}>Query History</Text>
          {historyResponse?.data?.total !== undefined && (
            <Badge size="sm" variant="light" color="gray">
              {historyResponse.data.total} queries
            </Badge>
          )}
        </Group>
      }
      size="xl"
      radius="lg"
      padding="lg"
      overlayProps={{
        backgroundOpacity: 0.55,
        blur: 3,
      }}
      styles={{
        header: {
          borderBottom: "1px solid var(--mantine-color-gray-2)",
          paddingBottom: 12,
        },
        body: {
          padding: 0,
        },
      }}
    >
      <Stack gap={0}>
        {/* Search and Actions Bar */}
        <Box p="md" className={classes.searchBar}>
          <Group gap="sm">
            <TextInput
              placeholder="Search queries..."
              leftSection={<IconSearch size={14} />}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{ flex: 1 }}
              size="sm"
              radius="md"
            />
            <Tooltip label="Refresh" position="bottom" withArrow>
              <ActionIcon 
                variant="light" 
                size="lg" 
                radius="md"
                onClick={() => refetch()}
                loading={isLoading}
              >
                <IconRefresh size={16} />
              </ActionIcon>
            </Tooltip>
          </Group>
        </Box>

        <Divider />

        {/* Content Area */}
        <Box className={classes.content}>
          {isLoading ? (
            <Stack gap="sm" p="md">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} height={80} radius="md" />
              ))}
            </Stack>
          ) : error ? (
            <Center p="xl">
              <Alert 
                icon={<IconAlertCircle size={16} />} 
                color="red" 
                variant="light"
                radius="md"
              >
                Failed to load query history. Please try again.
              </Alert>
            </Center>
          ) : filteredQueries.length === 0 ? (
            <Center p="xl">
              <Stack align="center" gap="sm">
                <ThemeIcon size={48} variant="light" color="gray" radius="xl">
                  <IconHistory size={24} />
                </ThemeIcon>
                <Text size="sm" c="dimmed" ta="center">
                  {searchQuery ? "No queries match your search" : "No query history yet"}
                </Text>
                {searchQuery && (
                  <Button 
                    variant="subtle" 
                    size="xs" 
                    onClick={() => setSearchQuery("")}
                  >
                    Clear search
                  </Button>
                )}
              </Stack>
            </Center>
          ) : (
            <ScrollArea h={450} offsetScrollbars scrollbarSize={6}>
              <Stack gap="xs" p="md">
                {filteredQueries.map((query) => {
                  const statusConfig = getStatusConfig(query.status);
                  const StatusIcon = statusConfig.icon;

                  return (
                    <Paper
                      key={query.jobId}
                      className={classes.queryCard}
                      p="sm"
                      radius="md"
                      withBorder
                    >
                      {/* Query Header */}
                      <Group justify="space-between" mb="xs">
                        <Group gap="xs">
                          <Badge
                            size="sm"
                            variant="light"
                            color={statusConfig.color}
                            leftSection={<StatusIcon size={12} />}
                          >
                            {statusConfig.label}
                          </Badge>
                          <Text size="xs" c="dimmed">
                            {formatDate(query.createdAt)}
                          </Text>
                        </Group>
                        <Group gap={4}>
                          <CopyButton value={query.queryString || ""}>
                            {({ copied, copy }) => (
                              <Tooltip label={copied ? "Copied!" : "Copy query"} position="left" withArrow>
                                <ActionIcon
                                  variant="subtle"
                                  size="sm"
                                  color={copied ? "teal" : "gray"}
                                  onClick={copy}
                                  disabled={!query.queryString}
                                >
                                  {copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
                                </ActionIcon>
                              </Tooltip>
                            )}
                          </CopyButton>
                          <Tooltip label="Use this query" position="left" withArrow>
                            <ActionIcon
                              variant="light"
                              size="sm"
                              color="teal"
                              onClick={() => query.queryString && handleSelectQuery(query.queryString)}
                              disabled={!query.queryString}
                            >
                              <IconPlayerPlay size={14} />
                            </ActionIcon>
                          </Tooltip>
                        </Group>
                      </Group>

                      {/* Query SQL Preview */}
                      <Code block className={classes.queryCode}>
                        {truncateSql(query.queryString, 200)}
                      </Code>

                      {/* Query Stats */}
                      <Group gap="md" mt="xs">
                        {query.completedAt && query.createdAt && (
                          <Group gap={4}>
                            <IconClock size={12} color="var(--mantine-color-dimmed)" />
                            <Text size="xs" c="dimmed">
                              {formatDuration(query.completedAt - query.createdAt)}
                            </Text>
                          </Group>
                        )}
                        {query.dataScannedInBytes && (
                          <Group gap={4}>
                            <IconDatabase size={12} color="var(--mantine-color-dimmed)" />
                            <Text size="xs" c="dimmed">
                              {formatBytes(query.dataScannedInBytes)}
                            </Text>
                          </Group>
                        )}
                      </Group>

                      {/* Error Message */}
                      {query.errorMessage && (
                        <Alert
                          mt="xs"
                          color="red"
                          variant="light"
                          radius="sm"
                          p="xs"
                          icon={<IconAlertCircle size={12} />}
                        >
                          <Text size="xs" lineClamp={2}>
                            {query.errorMessage}
                          </Text>
                        </Alert>
                      )}
                    </Paper>
                  );
                })}
              </Stack>
            </ScrollArea>
          )}
        </Box>

        {/* Footer */}
        <Divider />
        <Box p="sm" className={classes.footer}>
          <Group justify="space-between">
            <Text size="xs" c="dimmed">
              {filteredQueries.length} {filteredQueries.length === 1 ? "query" : "queries"} shown
            </Text>
            <Button variant="subtle" size="xs" onClick={onClose}>
              Close
            </Button>
          </Group>
        </Box>
      </Stack>
    </Modal>
  );
}

