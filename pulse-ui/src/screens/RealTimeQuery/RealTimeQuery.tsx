import {
  Box,
  Button,
  Grid,
  Stack,
  Text,
  Badge,
  ActionIcon,
  Tooltip,
  Loader,
  Center,
  Alert,
  CopyButton,
  ScrollArea,
  TextInput,
  Group,
  Divider,
} from "@mantine/core";
import {
  IconPlayerPlay,
  IconPlayerStop,
  IconHistory,
  IconSparkles,
  IconDatabase,
  IconTable,
  IconSearch,
  IconAlertCircle,
  IconRefresh,
  IconCopy,
  IconCheck,
  IconCircleCheck,
  IconCircleX,
  IconClock,
  IconPlayerPause,
  IconActivity,
} from "@tabler/icons-react";
import { useState, useMemo, useCallback } from "react";
import { notifications } from "@mantine/notifications";

import { SqlEditor } from "./components/SqlEditor";
import { QueryResults } from "./components/QueryResults";

import { QueryResult, VisualizationConfig, ColumnMetadata } from "./RealTimeQuery.interface";
import { useQueryExecution } from "./hooks";
import { useQueryMetadata } from "../../hooks";

import {
  REALTIME_QUERY_TEXTS,
  formatBytes,
  formatDuration,
  getTypeColor,
} from "./RealTimeQuery.constants";

import classes from "./RealTimeQuery.module.css";

export function RealTimeQuery() {
  // SQL query state
  const [sqlQuery, setSqlQuery] = useState<string>("");
  const [searchQuery, setSearchQuery] = useState<string>("");

  // Always use table view
  const visualization: VisualizationConfig = {
    chartType: "table",
    showLegend: false,
  };

  // Fetch table metadata from API
  const {
    data: metadataResponse,
    isLoading: isLoadingMetadata,
    error: metadataError,
    refetch: refetchMetadata,
  } = useQueryMetadata();

  // Query execution hook
  const {
    executeQuery,
    cancelQuery,
    loadMore,
    executionState,
    result,
    isLoading: isQueryLoading,
    isLoadingMore,
  } = useQueryExecution({
    onSuccess: (queryResult: QueryResult) => {
      notifications.show({
        title: "Query Completed",
        message: `Retrieved ${queryResult.totalRows} rows in ${formatDuration(queryResult.executionTimeMs || 0)}`,
        color: "teal",
      });
    },
    onError: (error: string) => {
      notifications.show({
        title: "Query Failed",
        message: error,
        color: "red",
      });
    },
  });

  // Extract metadata
  const tableMetadata = metadataResponse?.data;
  const tableName = tableMetadata?.tableName || "";
  const databaseName = tableMetadata?.databaseName || "";
  const fullTableName = tableName ? `${databaseName}.${tableName}` : "";

  // Memoize columns to avoid dependency issues
  const columns = useMemo(() => {
    return tableMetadata?.columns || [];
  }, [tableMetadata?.columns]);

  // Filter columns by search
  const filteredColumns = useMemo(() => {
    if (!searchQuery.trim()) return columns;
    const query = searchQuery.toLowerCase();
    return columns.filter((col) => col.name.toLowerCase().includes(query));
  }, [columns, searchQuery]);

  // Handlers
  const handleSqlChange = useCallback((value: string) => {
    setSqlQuery(value);
  }, []);

  const handleRunQuery = useCallback(() => {
    if (!sqlQuery.trim()) {
      notifications.show({
        title: "Error",
        message: REALTIME_QUERY_TEXTS.EMPTY_QUERY,
        color: "orange",
      });
      return;
    }
    executeQuery(sqlQuery);
  }, [sqlQuery, executeQuery]);

  const handleCancelQuery = useCallback(() => {
    cancelQuery();
    notifications.show({
      title: "Query Cancelled",
      message: REALTIME_QUERY_TEXTS.QUERY_CANCELLED,
      color: "orange",
    });
  }, [cancelQuery]);

  const canRunQuery = sqlQuery.trim().length > 0;
  const isRunning = executionState.status === "submitting" || executionState.status === "polling";

  // Get status message
  const getStatusMessage = () => {
    switch (executionState.status) {
      case "submitting":
        return "Submitting query...";
      case "polling":
        return `Fetching results... (attempt ${executionState.pollCount})`;
      case "completed":
        return "Query completed successfully";
      case "failed":
        return executionState.errorMessage || "Query failed";
      case "cancelled":
        return "Query was cancelled";
      default:
        return null;
    }
  };

  return (
    <Box className={classes.pageContainer}>
      {/* Page Header */}
      <Box className={classes.pageHeader}>
        <Box className={classes.headerContent}>
          <Box className={classes.titleSection}>
            <h1 className={classes.pageTitle}>{REALTIME_QUERY_TEXTS.PAGE_TITLE}</h1>
            <p className={classes.pageSubtitle}>{REALTIME_QUERY_TEXTS.PAGE_SUBTITLE}</p>
          </Box>
          <Box className={classes.actionsSection}>
            <Tooltip label="Query History (Coming Soon)">
              <ActionIcon variant="light" size="lg" color="teal" disabled>
                <IconHistory size={18} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label="Templates (Coming Soon)">
              <ActionIcon variant="light" size="lg" color="teal" disabled>
                <IconSparkles size={18} />
              </ActionIcon>
            </Tooltip>
            {isRunning ? (
              <Button
                color="red"
                leftSection={<IconPlayerStop size={16} />}
                onClick={handleCancelQuery}
                size="sm"
                className={classes.cancelButton}
              >
                Cancel
              </Button>
            ) : (
              <Button
                color="teal"
                leftSection={<IconPlayerPlay size={16} />}
                onClick={handleRunQuery}
                disabled={!canRunQuery}
                size="sm"
                className={classes.primaryButton}
              >
                Run Query
              </Button>
            )}
          </Box>
        </Box>
      </Box>

      {/* Main Content */}
      <Grid gutter="md">
        {/* Left Sidebar - Combined Data Source & Columns */}
        <Grid.Col span={{ base: 12, md: 3 }}>
          <Box className={classes.schemaCard}>
            {/* Data Source Header */}
            <Box className={classes.schemaHeader}>
              <Group gap="xs">
                <IconDatabase size={16} className={classes.cardIcon} />
                <Text className={classes.cardTitle}>Schema</Text>
              </Group>
            </Box>

            {isLoadingMetadata ? (
              <Center py="xl">
                <Stack align="center" gap="xs">
                  <Loader size="sm" color="teal" />
                  <Text size="xs" c="dimmed">{REALTIME_QUERY_TEXTS.LOADING_METADATA}</Text>
                </Stack>
              </Center>
            ) : metadataError ? (
              <Box p="md">
                <Alert
                  icon={<IconAlertCircle size={16} />}
                  color="red"
                  variant="light"
                  title="Error"
                >
                  <Text size="xs">{REALTIME_QUERY_TEXTS.METADATA_ERROR}</Text>
                  <Button
                    size="xs"
                    variant="light"
                    color="red"
                    mt="xs"
                    leftSection={<IconRefresh size={14} />}
                    onClick={() => refetchMetadata()}
                  >
                    Retry
                  </Button>
                </Alert>
              </Box>
            ) : (
              <>
                {/* Table Info */}
                <Box className={classes.tableInfoSection}>
                  <CopyButton value={fullTableName}>
                    {({ copied, copy }) => (
                      <Tooltip label={copied ? "Copied!" : "Copy full table name"}>
                        <Box className={classes.tableInfoCard} onClick={copy}>
                          <Group gap="xs" wrap="nowrap">
                            <IconTable size={16} color="var(--mantine-color-teal-6)" />
                            <Box style={{ flex: 1, overflow: "hidden" }}>
                              <Text size="xs" fw={600} truncate>{tableName}</Text>
                              <Text size="xs" c="dimmed" truncate>{databaseName}</Text>
                            </Box>
                            <ActionIcon variant="subtle" size="xs" color={copied ? "teal" : "gray"}>
                              {copied ? <IconCheck size={12} /> : <IconCopy size={12} />}
                            </ActionIcon>
                          </Group>
                        </Box>
                      </Tooltip>
                    )}
                  </CopyButton>
                </Box>

                <Divider />

                {/* Columns Section */}
                <Box className={classes.columnsSection}>
                  <Group justify="space-between" mb="xs">
                    <Text size="xs" fw={600} c="dimmed" tt="uppercase">Columns</Text>
                    <Badge size="xs" variant="light" color="teal">
                      {columns.length}
                    </Badge>
                  </Group>
                  
                  <TextInput
                    size="xs"
                    placeholder="Search columns..."
                    leftSection={<IconSearch size={14} />}
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    mb="xs"
                  />
                  
                  <ScrollArea className={classes.columnsScrollArea} type="auto" offsetScrollbars>
                    <Stack gap={4}>
                      {filteredColumns.length === 0 ? (
                        <Text size="xs" c="dimmed" ta="center" py="md">
                          No columns found
                        </Text>
                      ) : (
                        filteredColumns.map((column: ColumnMetadata) => (
                          <CopyButton key={column.name} value={column.name}>
                            {({ copied, copy }) => (
                              <Tooltip
                                label={copied ? "Copied!" : "Click to copy"}
                                position="right"
                              >
                                <Box
                                  className={classes.columnItem}
                                  onClick={copy}
                                  data-copied={copied}
                                >
                                  <Group gap="xs" wrap="nowrap" justify="space-between">
                                    <Text size="xs" fw={500} truncate style={{ flex: 1 }}>
                                      {column.name}
                                    </Text>
                                    <Badge size="xs" variant="light" color={getTypeColor(column.type)}>
                                      {column.type}
                                    </Badge>
                                  </Group>
                                </Box>
                              </Tooltip>
                            )}
                          </CopyButton>
                        ))
                      )}
                    </Stack>
                  </ScrollArea>
                  
                  <Text size="xs" c="dimmed" ta="center" mt="xs">
                    {filteredColumns.length} of {columns.length} columns
                  </Text>
                </Box>
              </>
            )}
          </Box>
        </Grid.Col>

        {/* Main - SQL Editor */}
        <Grid.Col span={{ base: 12, md: 6 }}>
          <SqlEditor
            value={sqlQuery}
            onChange={handleSqlChange}
            tableName={fullTableName || undefined}
            isLoading={isLoadingMetadata}
          />
        </Grid.Col>

        {/* Right Sidebar - Query Status */}
        <Grid.Col span={{ base: 12, md: 3 }}>
          <Box className={classes.rightSidebar}>
            {/* Query Status */}
            <Box className={classes.statusSection}>
              {/* Status Header with Live Indicator */}
              <Box className={classes.statusHeader}>
                <Group gap="xs" justify="space-between">
                  <Group gap="xs">
                    <IconActivity size={16} className={classes.cardIcon} />
                    <Text className={classes.cardTitle}>Query Status</Text>
                  </Group>
                  {isRunning && (
                    <Badge 
                      size="xs" 
                      variant="dot" 
                      color="blue"
                      className={classes.liveBadge}
                    >
                      Live
                    </Badge>
                  )}
                </Group>
              </Box>
              
              <Box className={classes.statusContent}>
                {/* Main Status Card */}
                <Box 
                  className={classes.statusCard}
                  data-status={isRunning ? "running" : executionState.status}
                >
                  {/* Status Icon with Animation */}
                  <Box className={classes.statusIconContainer}>
                    {isRunning && <Box className={classes.pulseRing} />}
                    <Box 
                      className={classes.statusIconWrapper}
                      data-status={isRunning ? "running" : executionState.status}
                    >
                      {executionState.status === "idle" && <IconClock size={24} />}
                      {isRunning && <Loader size={24} color="white" />}
                      {executionState.status === "completed" && <IconCircleCheck size={24} />}
                      {executionState.status === "failed" && <IconCircleX size={24} />}
                      {executionState.status === "cancelled" && <IconPlayerPause size={24} />}
                    </Box>
                  </Box>
                  
                  {/* Status Text */}
                  <Text className={classes.statusTitle}>
                    {executionState.status === "idle" ? "Ready to Query" : 
                     isRunning ? "Executing Query..." :
                     executionState.status === "completed" ? "Query Completed" :
                     executionState.status === "failed" ? "Query Failed" :
                     executionState.status === "cancelled" ? "Query Cancelled" : 
                     executionState.status}
                  </Text>
                  <Text className={classes.statusSubtitle}>
                    {getStatusMessage() || "Write a SQL query and click Run"}
                  </Text>
                  
                  {/* Progress bar for running state */}
                  {isRunning && (
                    <Box className={classes.progressBar}>
                      <Box className={classes.progressFill} />
                    </Box>
                  )}
                </Box>

                {/* Job ID - Compact */}
                {executionState.jobId && (
                  <CopyButton value={executionState.jobId}>
                    {({ copied, copy }) => (
                      <Tooltip label={copied ? "Copied!" : "Click to copy full Job ID"}>
                        <Box className={classes.jobIdRow} onClick={copy}>
                          <Text size="xs" c="dimmed" fw={500}>Job</Text>
                          <Group gap={6}>
                            <Text size="xs" ff="monospace" c="dark.4">
                              {executionState.jobId?.slice(0, 8)}
                            </Text>
                            {copied ? (
                              <IconCheck size={12} color="var(--mantine-color-teal-6)" />
                            ) : (
                              <IconCopy size={12} color="var(--mantine-color-gray-5)" />
                            )}
                          </Group>
                        </Box>
                      </Tooltip>
                    )}
                  </CopyButton>
                )}

                {/* Stats - Only show when we have results */}
                {result && (
                  <Stack gap="xs" className={classes.statsContainer}>
                    <Box className={classes.statItem}>
                      <Box className={classes.statIconWrapper} data-type="rows">
                        <IconTable size={14} />
                      </Box>
                      <Box className={classes.statInfo}>
                        <Text className={classes.statValue}>{result.totalRows.toLocaleString()}</Text>
                        <Text className={classes.statLabel}>rows returned</Text>
                      </Box>
                    </Box>
                    
                    {result.executionTimeMs !== undefined && (
                      <Box className={classes.statItem}>
                        <Box className={classes.statIconWrapper} data-type="time">
                          <IconClock size={14} />
                        </Box>
                        <Box className={classes.statInfo}>
                          <Text className={classes.statValue}>{formatDuration(result.executionTimeMs)}</Text>
                          <Text className={classes.statLabel}>execution time</Text>
                        </Box>
                      </Box>
                    )}
                    
                    {result.dataScannedInBytes !== undefined && (
                      <Box className={classes.statItem}>
                        <Box className={classes.statIconWrapper} data-type="data">
                          <IconDatabase size={14} />
                        </Box>
                        <Box className={classes.statInfo}>
                          <Text className={classes.statValue}>{formatBytes(result.dataScannedInBytes)}</Text>
                          <Text className={classes.statLabel}>data scanned</Text>
                        </Box>
                      </Box>
                    )}
                    
                    {result.hasMore && (
                      <Box className={classes.moreDataBanner}>
                        <Text size="xs" fw={500}>More data available</Text>
                        <Text size="xs" c="dimmed">Click "Load More" in results</Text>
                      </Box>
                    )}
                  </Stack>
                )}
              </Box>
            </Box>
          </Box>
        </Grid.Col>
      </Grid>

      {/* Results Section */}
      <Box className={classes.resultsSection}>
        <h2 className={classes.sectionTitle}>
          Results
          {result && (
            <Badge size="sm" variant="light" color="teal" ml="sm">
              {result.totalRows.toLocaleString()} rows
            </Badge>
          )}
        </h2>
        <QueryResults
          data={result}
          visualization={visualization}
          isLoading={isQueryLoading}
          isLoadingMore={isLoadingMore}
          error={executionState.errorMessage}
          onRefresh={handleRunQuery}
          onLoadMore={loadMore}
        />
      </Box>
    </Box>
  );
}
