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
  SegmentedControl,
} from "@mantine/core";
import {
  IconPlayerPlay,
  IconPlayerStop,
  IconHistory,
  IconDatabase,
  IconTable,
  IconSearch,
  IconAlertCircle,
  IconRefresh,
  IconCopy,
  IconCheck,
  IconCode,
  IconWand,
  IconSparkles,
} from "@tabler/icons-react";
import { useState, useMemo, useCallback } from "react";
import { notifications } from "@mantine/notifications";

import { SqlEditor } from "./components/SqlEditor";
import { QueryResults } from "./components/QueryResults";
import { QueryBuilder } from "./components/QueryBuilder";
import { QueryHistory } from "./components/QueryHistory";
import { AiChat } from "./components/AiChat";

import { QueryResult, VisualizationConfig, ColumnMetadata, TableMetadata } from "./RealTimeQuery.interface";
import { useQueryExecution } from "./hooks";
import { useQueryMetadata } from "../../hooks";

import {
  REALTIME_QUERY_TEXTS,
  formatDuration,
  getTypeColor,
} from "./RealTimeQuery.constants";

import classes from "./RealTimeQuery.module.css";

// Query mode type
type QueryMode = "builder" | "sql" | "ai";

export function RealTimeQuery() {
  // Query mode state
  const [queryMode, setQueryMode] = useState<QueryMode>("ai");
  
  // SQL query state (for SQL mode)
  const [sqlQuery, setSqlQuery] = useState<string>("");
  // Builder generated SQL (for Builder mode)
  const [builderSql, setBuilderSql] = useState<string>("");

  // Handle mode change - copy builder SQL to editor when switching to SQL mode
  const handleModeChange = useCallback((value: string) => {
    const newMode = value as QueryMode;
    
    // When switching from builder to SQL, copy the generated SQL
    if (queryMode === "builder" && newMode === "sql" && builderSql) {
      setSqlQuery(builderSql);
    }
    
    setQueryMode(newMode);
  }, [queryMode, builderSql]);
  
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [historyModalOpen, setHistoryModalOpen] = useState<boolean>(false);

  // Handle selecting a query from history
  const handleSelectHistoryQuery = useCallback((query: string) => {
    setSqlQuery(query);
    setQueryMode("sql"); // Switch to SQL mode to show the selected query
  }, []);

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

  // Query execution hook (for builder and SQL modes)
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

  // Extract metadata - API returns array of tables, use the first one
  const tables = metadataResponse?.data || [];
  const tableMetadata: TableMetadata | undefined = tables[0];
  const tableName = tableMetadata?.tableName || "";
  const databaseName = tableMetadata?.tableSchema || "";
  const fullTableName = tableName ? `${databaseName}.${tableName}` : "";

  // Memoize columns to avoid dependency issues
  const columns = useMemo(() => {
    return tableMetadata?.columns || [];
  }, [tableMetadata?.columns]);

  // Filter columns by search
  const filteredColumns = useMemo(() => {
    if (!searchQuery.trim()) return columns;
    const query = searchQuery.toLowerCase();
    return columns.filter((col) => col.columnName.toLowerCase().includes(query));
  }, [columns, searchQuery]);

  // Handlers
  const handleSqlChange = useCallback((value: string) => {
    setSqlQuery(value);
  }, []);

  const handleRunQuery = useCallback(() => {
    const queryToRun = queryMode === "sql" ? sqlQuery : builderSql;
    if (!queryToRun.trim()) {
      notifications.show({
        title: "Error",
        message: REALTIME_QUERY_TEXTS.EMPTY_QUERY,
        color: "orange",
      });
      return;
    }
    executeQuery(queryToRun);
  }, [queryMode, sqlQuery, builderSql, executeQuery]);

  // Handle builder SQL changes
  const handleBuilderSqlChange = useCallback((sql: string) => {
    setBuilderSql(sql);
  }, []);

  const handleCancelQuery = useCallback(() => {
    cancelQuery();
    notifications.show({
      title: "Query Cancelled",
      message: REALTIME_QUERY_TEXTS.QUERY_CANCELLED,
      color: "orange",
    });
  }, [cancelQuery]);

  const canRunQuery = queryMode === "sql"
    ? sqlQuery.trim().length > 0
    : queryMode === "builder"
      ? builderSql.trim().length > 0
      : false; // AI mode has its own send button inside the chat
  const isSubmitting = executionState.status === "submitting";
  const isPolling = executionState.status === "polling";
  const canCancel = isPolling && !!executionState.jobId;

  // In AI mode, render the full-screen chat interface
  if (queryMode === "ai") {
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
              {/* Mode Toggle */}
              <SegmentedControl
                value={queryMode}
                onChange={handleModeChange}
                data={[
                  {
                    value: "ai",
                    label: (
                      <Group gap={6} wrap="nowrap">
                        <IconSparkles size={16} />
                        <Text size="sm" fw={600}>AI</Text>
                      </Group>
                    ),
                  },
                  {
                    value: "builder",
                    label: (
                      <Group gap={6} wrap="nowrap">
                        <IconWand size={16} />
                        <Text size="sm" fw={600}>Builder</Text>
                      </Group>
                    ),
                  },
                  {
                    value: "sql",
                    label: (
                      <Group gap={6} wrap="nowrap">
                        <IconCode size={16} />
                        <Text size="sm" fw={600}>Code</Text>
                      </Group>
                    ),
                  },
                ]}
                size="sm"
                className={classes.modeToggle}
              />

              <Tooltip label="Query History">
                <ActionIcon
                  variant="light"
                  size="lg"
                  color="teal"
                  onClick={() => setHistoryModalOpen(true)}
                >
                  <IconHistory size={18} />
                </ActionIcon>
              </Tooltip>
              {/* No Run Query button in AI mode — send happens from the chat input */}
            </Box>
          </Box>
        </Box>

        {/* AI Chat takes the full content area */}
        <AiChat />

        {/* Query History Modal */}
        <QueryHistory
          opened={historyModalOpen}
          onClose={() => setHistoryModalOpen(false)}
          onSelectQuery={handleSelectHistoryQuery}
        />
      </Box>
    );
  }

  // Builder / SQL modes
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
            {/* Mode Toggle */}
            <SegmentedControl
              value={queryMode}
              onChange={handleModeChange}
              data={[
                {
                  value: "ai",
                  label: (
                    <Group gap={6} wrap="nowrap">
                      <IconSparkles size={16} />
                      <Text size="sm" fw={600}>AI</Text>
                    </Group>
                  ),
                },
                {
                  value: "builder",
                  label: (
                    <Group gap={6} wrap="nowrap">
                      <IconWand size={16} />
                      <Text size="sm" fw={600}>Builder</Text>
                    </Group>
                  ),
                },
                {
                  value: "sql",
                  label: (
                    <Group gap={6} wrap="nowrap">
                      <IconCode size={16} />
                      <Text size="sm" fw={600}>Code</Text>
                    </Group>
                  ),
                },
              ]}
              size="sm"
              className={classes.modeToggle}
            />
            
            <Tooltip label="Query History">
              <ActionIcon 
                variant="light" 
                size="lg" 
                color="teal"
                onClick={() => setHistoryModalOpen(true)}
              >
                <IconHistory size={18} />
              </ActionIcon>
            </Tooltip>
            
            {isSubmitting ? (
              <Button
                color="gray"
                leftSection={<Loader size={16} color="white" />}
                size="sm"
                disabled
              >
                Submitting...
              </Button>
            ) : canCancel ? (
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
        {/* Left Sidebar - Schema (Only in SQL mode) */}
        {queryMode === "sql" && (
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
                          <CopyButton key={column.columnName} value={column.columnName}>
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
                                      {column.columnName}
                                    </Text>
                                    <Badge size="xs" variant="light" color={getTypeColor(column.dataType)}>
                                      {column.dataType}
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
        )}

        {/* Main - SQL Editor or Query Builder */}
        <Grid.Col span={{ base: 12, md: queryMode === "sql" ? 9 : 12 }}>
          {queryMode === "sql" ? (
            <SqlEditor
              value={sqlQuery}
              onChange={handleSqlChange}
              tableName={fullTableName || undefined}
              isLoading={isLoadingMetadata}
            />
          ) : (
            <QueryBuilder
              tableName={tableName}
              databaseName={databaseName}
              columns={columns}
              onQueryChange={handleBuilderSqlChange}
              isLoading={isLoadingMetadata}
            />
          )}
        </Grid.Col>
      </Grid>

      {/* Results Section */}
      <Box className={classes.resultsSection}>
        <Group justify="space-between" align="center" mb="md">
          <h2 className={classes.sectionTitle}>
            Results
            {result && (
              <Badge size="sm" variant="light" color="teal" ml="sm">
                {result.rows.length.toLocaleString()}
                {result.hasMore || result.rows.length < result.totalRows 
                  ? ` of ${result.totalRows.toLocaleString()}` 
                  : ""} rows
              </Badge>
            )}
          </h2>
        </Group>

        <QueryResults
          data={result}
          visualization={visualization}
          isLoading={isQueryLoading}
          isLoadingMore={isLoadingMore}
          error={executionState.status === "cancelled" ? null : executionState.errorMessage}
          errorCause={executionState.status === "cancelled" ? null : executionState.errorCause}
          isCancelled={executionState.status === "cancelled"}
          onRefresh={handleRunQuery}
          onLoadMore={loadMore}
        />
      </Box>

      {/* Query History Modal */}
      <QueryHistory
        opened={historyModalOpen}
        onClose={() => setHistoryModalOpen(false)}
        onSelectQuery={handleSelectHistoryQuery}
      />
    </Box>
  );
}
