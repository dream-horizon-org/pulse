import {
  Box,
  Button,
  Grid,
  Stack,
  Text,
  Badge,
  Collapse,
  ActionIcon,
  Tooltip,
  Tabs,
} from "@mantine/core";
import {
  IconPlayerPlay,
  IconPlayerStop,
  IconBookmark,
  IconHistory,
  IconSparkles,
  IconChevronDown,
  IconChevronUp,
  IconDatabase,
  IconFilter,
  IconChartBar,
  IconLayoutGrid,
  IconClock,
  IconEye,
  IconCode,
  IconWand,
  IconTable,
} from "@tabler/icons-react";
import { useState, useMemo, useCallback } from "react";
import { useDisclosure } from "@mantine/hooks";

import { DataSourceSelector } from "./components/DataSourceSelector";
import { FilterBuilder } from "./components/FilterBuilder";
import { MetricSelector } from "./components/MetricSelector";
import { GroupBySelector } from "./components/GroupBySelector";
import { TimeRangeSelector } from "./components/TimeRangeSelector";
import { VisualizationSelector } from "./components/VisualizationSelector";
import { QueryResults } from "./components/QueryResults";
import { FieldsList } from "./components/FieldsList";
import { SqlEditor } from "./components/SqlEditor";

import {
  QueryState,
  DataSourceType,
  FieldMetadata,
  FilterGroup,
  Metric,
  GroupByDimension,
  TimeRange,
  VisualizationConfig,
  QueryResult,
} from "./RealTimeQuery.interface";

import {
  DEFAULT_QUERY_STATE,
  EVENTS_FIELDS,
  USERS_FIELDS,
  SESSIONS_FIELDS,
  REALTIME_QUERY_TEXTS,
} from "./RealTimeQuery.constants";

import classes from "./RealTimeQuery.module.css";

type QueryMode = "visual" | "sql";

// Mock data generator for demonstration
function generateMockData(queryState: QueryState): QueryResult {
  const columns = [
    { name: queryState.groupBy[0]?.field || "date", type: "string" },
    ...queryState.metrics.map((m) => ({
      name: m.alias || `${m.aggregation}(${m.field || "*"})`,
      type: "number",
    })),
  ];

  const rows = Array.from({ length: 12 }, (_, i) => {
    const row: Record<string, string | number> = {};
    row[columns[0].name] = `2024-01-${String(i + 1).padStart(2, "0")}`;
    queryState.metrics.forEach((m) => {
      const colName = m.alias || `${m.aggregation}(${m.field || "*"})`;
      row[colName] = Math.floor(Math.random() * 10000) + 1000;
    });
    return row;
  });

  return {
    columns,
    rows,
    totalRows: rows.length,
    executionTimeMs: Math.floor(Math.random() * 500) + 100,
    hasMore: false,
  };
}

// Mock SQL result generator
function generateMockSqlData(): QueryResult {
  const columns = [
    { name: "eventName", type: "string" },
    { name: "event_count", type: "number" },
    { name: "avg_duration", type: "number" },
  ];

  const eventNames = ["AppLaunch", "ScreenView", "ButtonClick", "ApiCall", "Purchase", "Scroll"];
  const rows = eventNames.map((name) => ({
    eventName: name,
    event_count: Math.floor(Math.random() * 50000) + 1000,
    avg_duration: Math.floor(Math.random() * 500) + 50,
  }));

  return {
    columns,
    rows,
    totalRows: rows.length,
    executionTimeMs: Math.floor(Math.random() * 800) + 200,
    hasMore: false,
  };
}

export function RealTimeQuery() {
  // Query mode
  const [queryMode, setQueryMode] = useState<QueryMode>("visual");
  
  // Query state for visual builder
  const [queryState, setQueryState] = useState<QueryState>(DEFAULT_QUERY_STATE);
  
  // SQL query state
  const [sqlQuery, setSqlQuery] = useState<string>("");
  const [isSqlValid, setIsSqlValid] = useState<boolean | undefined>(undefined);
  const [isValidatingSql, setIsValidatingSql] = useState(false);
  
  // UI state
  const [isRunning, setIsRunning] = useState(false);
  const [queryResult, setQueryResult] = useState<QueryResult | null>(null);
  const [queryError, setQueryError] = useState<string | null>(null);
  
  // Section collapse states
  const [filtersOpened, { toggle: toggleFilters }] = useDisclosure(true);
  const [groupByOpened, { toggle: toggleGroupBy }] = useDisclosure(true);

  // Get fields based on data source
  const fields = useMemo((): FieldMetadata[] => {
    switch (queryState.dataSource) {
      case "events":
        return EVENTS_FIELDS;
      case "users":
        return USERS_FIELDS;
      case "sessions":
        return SESSIONS_FIELDS;
      default:
        return EVENTS_FIELDS;
    }
  }, [queryState.dataSource]);

  // Update handlers
  const handleDataSourceChange = useCallback((dataSource: DataSourceType) => {
    setQueryState((prev) => ({
      ...prev,
      dataSource,
      filters: { ...prev.filters, conditions: [] },
      groupBy: [],
    }));
    setQueryResult(null);
  }, []);

  const handleFiltersChange = useCallback((filters: FilterGroup) => {
    setQueryState((prev) => ({ ...prev, filters }));
  }, []);

  const handleMetricsChange = useCallback((metrics: Metric[]) => {
    setQueryState((prev) => ({ ...prev, metrics }));
  }, []);

  const handleGroupByChange = useCallback((groupBy: GroupByDimension[]) => {
    setQueryState((prev) => ({ ...prev, groupBy }));
  }, []);

  const handleTimeRangeChange = useCallback((timeRange: TimeRange) => {
    setQueryState((prev) => ({ ...prev, timeRange }));
  }, []);

  const handleVisualizationChange = useCallback((visualization: VisualizationConfig) => {
    setQueryState((prev) => ({ ...prev, visualization }));
  }, []);

  // SQL handlers
  const handleSqlChange = useCallback((value: string) => {
    setSqlQuery(value);
    setIsSqlValid(undefined);
  }, []);

  const handleValidateSql = useCallback(() => {
    setIsValidatingSql(true);
    setTimeout(() => {
      const isValid = sqlQuery.trim().toUpperCase().startsWith("SELECT");
      setIsSqlValid(isValid);
      setIsValidatingSql(false);
    }, 500);
  }, [sqlQuery]);

  // Run query
  const handleRunQuery = useCallback(async () => {
    setIsRunning(true);
    setQueryError(null);

    setTimeout(() => {
      try {
        let result: QueryResult;
        if (queryMode === "sql") {
          result = generateMockSqlData();
        } else {
          result = generateMockData(queryState);
        }
        setQueryResult(result);
      } catch {
        setQueryError("Failed to execute query. Please try again.");
      } finally {
        setIsRunning(false);
      }
    }, 1500);
  }, [queryMode, queryState]);

  const handleCancelQuery = useCallback(() => {
    setIsRunning(false);
  }, []);

  const handleExportCSV = useCallback(() => {
    if (!queryResult) return;
    
    const headers = queryResult.columns.map((c) => c.name).join(",");
    const rows = queryResult.rows.map((row) =>
      queryResult.columns.map((c) => row[c.name]).join(",")
    );
    const csv = [headers, ...rows].join("\n");
    
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "query-results.csv";
    a.click();
    URL.revokeObjectURL(url);
  }, [queryResult]);

  const canRunQuery = queryMode === "visual" 
    ? queryState.metrics.length > 0 
    : sqlQuery.trim().length > 0;

  return (
    <Tabs
      value={queryMode}
      onChange={(value) => {
        setQueryMode(value as QueryMode);
        setQueryResult(null);
        setQueryError(null);
      }}
      variant="unstyled"
      classNames={{
        root: classes.tabsRoot,
        list: classes.tabsList,
        tab: classes.tab,
      }}
    >
      <Box className={classes.pageContainer}>
        {/* Page Header with integrated tabs */}
        <Box className={classes.pageHeader}>
          <Box className={classes.headerContent}>
            <Box className={classes.titleSection}>
              <h1 className={classes.pageTitle}>{REALTIME_QUERY_TEXTS.PAGE_TITLE}</h1>
              <p className={classes.pageSubtitle}>{REALTIME_QUERY_TEXTS.PAGE_SUBTITLE}</p>
            </Box>
            <Box className={classes.actionsSection}>
              <Tooltip label="Query History">
                <ActionIcon variant="light" size="lg" color="teal">
                  <IconHistory size={18} />
                </ActionIcon>
              </Tooltip>
              <Tooltip label="Templates">
                <ActionIcon variant="light" size="lg" color="teal">
                  <IconSparkles size={18} />
                </ActionIcon>
              </Tooltip>
              <Button
                variant="light"
                color="teal"
                leftSection={<IconBookmark size={16} />}
                size="sm"
              >
                Save Query
              </Button>
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

        {/* Mode Tabs */}
        <Tabs.List className={classes.modeTabsList}>
          <Tabs.Tab value="visual" leftSection={<IconWand size={16} />}>
            Visual Builder
          </Tabs.Tab>
          <Tabs.Tab value="sql" leftSection={<IconCode size={16} />}>
            SQL Editor
          </Tabs.Tab>
        </Tabs.List>

      {/* Visual Builder Mode */}
      {queryMode === "visual" && (
        <Grid gutter="md">
          {/* Left Sidebar - Data Source & Fields */}
          <Grid.Col span={{ base: 12, md: 3 }}>
            <Stack gap="md">
              {/* Data Source */}
              <Box className={classes.card}>
                <Box className={classes.cardHeader}>
                  <IconDatabase size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Data Source</Text>
                </Box>
                <DataSourceSelector
                  value={queryState.dataSource}
                  onChange={handleDataSourceChange}
                />
              </Box>

              {/* Available Fields */}
              <Box className={classes.card}>
                <Box className={classes.cardHeader}>
                  <IconTable size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Available Fields</Text>
                </Box>
                <FieldsList fields={fields} />
              </Box>
            </Stack>
          </Grid.Col>

          {/* Middle - Query Builder */}
          <Grid.Col span={{ base: 12, md: 6 }}>
            <Stack gap="md">
              {/* Metrics */}
              <Box className={classes.card}>
                <Box className={classes.cardHeader}>
                  <IconChartBar size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Metrics</Text>
                  <Badge size="xs" variant="light" color="teal" ml="auto">
                    {queryState.metrics.length}
                  </Badge>
                </Box>
                <MetricSelector
                  metrics={queryState.metrics}
                  fields={fields}
                  onChange={handleMetricsChange}
                />
              </Box>

              {/* Filters */}
              <Box className={classes.card}>
                <Box 
                  className={classes.collapsibleHeader} 
                  onClick={toggleFilters}
                >
                  <IconFilter size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Filters</Text>
                  <Badge size="xs" variant="light" color="blue" ml="xs">
                    {queryState.filters.conditions.length}
                  </Badge>
                  <Box style={{ flex: 1 }} />
                  <ActionIcon variant="subtle" size="sm" color="gray">
                    {filtersOpened ? <IconChevronUp size={14} /> : <IconChevronDown size={14} />}
                  </ActionIcon>
                </Box>
                <Collapse in={filtersOpened}>
                  <Box mt="sm">
                    <FilterBuilder
                      filters={queryState.filters}
                      fields={fields}
                      onChange={handleFiltersChange}
                    />
                  </Box>
                </Collapse>
              </Box>

              {/* Group By */}
              <Box className={classes.card}>
                <Box 
                  className={classes.collapsibleHeader} 
                  onClick={toggleGroupBy}
                >
                  <IconLayoutGrid size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Group By</Text>
                  <Badge size="xs" variant="light" color="violet" ml="xs">
                    {queryState.groupBy.length}
                  </Badge>
                  <Box style={{ flex: 1 }} />
                  <ActionIcon variant="subtle" size="sm" color="gray">
                    {groupByOpened ? <IconChevronUp size={14} /> : <IconChevronDown size={14} />}
                  </ActionIcon>
                </Box>
                <Collapse in={groupByOpened}>
                  <Box mt="sm">
                    <GroupBySelector
                      dimensions={queryState.groupBy}
                      fields={fields}
                      onChange={handleGroupByChange}
                    />
                  </Box>
                </Collapse>
              </Box>
            </Stack>
          </Grid.Col>

          {/* Right Sidebar - Time Range & Visualization */}
          <Grid.Col span={{ base: 12, md: 3 }}>
            <Stack gap="md">
              {/* Time Range */}
              <Box className={classes.card}>
                <Box className={classes.cardHeader}>
                  <IconClock size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Time Range</Text>
                </Box>
                <TimeRangeSelector
                  value={queryState.timeRange}
                  onChange={handleTimeRangeChange}
                />
              </Box>

              {/* Visualization */}
              <Box className={classes.card}>
                <Box className={classes.cardHeader}>
                  <IconEye size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Visualization</Text>
                </Box>
                <VisualizationSelector
                  value={queryState.visualization}
                  onChange={handleVisualizationChange}
                  hasGroupBy={queryState.groupBy.length > 0}
                />
              </Box>

              {/* Query Stats */}
              <Box className={classes.statsCard}>
                <Text className={classes.statsTitle}>Query Summary</Text>
                <Box className={classes.statRow}>
                  <Text className={classes.statLabel}>Data Source</Text>
                  <Badge size="xs" variant="light" color="teal">
                    {queryState.dataSource}
                  </Badge>
                </Box>
                <Box className={classes.statRow}>
                  <Text className={classes.statLabel}>Metrics</Text>
                  <Text className={classes.statValue}>{queryState.metrics.length}</Text>
                </Box>
                <Box className={classes.statRow}>
                  <Text className={classes.statLabel}>Filters</Text>
                  <Text className={classes.statValue}>{queryState.filters.conditions.length}</Text>
                </Box>
                <Box className={classes.statRow}>
                  <Text className={classes.statLabel}>Dimensions</Text>
                  <Text className={classes.statValue}>{queryState.groupBy.length}</Text>
                </Box>
              </Box>
            </Stack>
          </Grid.Col>
        </Grid>
      )}

      {/* SQL Editor Mode */}
      {queryMode === "sql" && (
        <Grid gutter="md">
          {/* Left Sidebar - Tables & Fields */}
          <Grid.Col span={{ base: 12, md: 3 }}>
            <Stack gap="md">
              {/* Available Tables */}
              <Box className={classes.card}>
                <Box className={classes.cardHeader}>
                  <IconDatabase size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Available Tables</Text>
                </Box>
                <Stack gap="xs">
                  <Box className={classes.tableCard}>
                    <Text size="xs" fw={600}>processed_events_partitioned_hourly</Text>
                    <Text size="xs" c="dimmed">Main events table</Text>
                  </Box>
                  <Box className={classes.tableCard}>
                    <Text size="xs" fw={600}>users</Text>
                    <Text size="xs" c="dimmed">User profiles</Text>
                  </Box>
                  <Box className={classes.tableCard}>
                    <Text size="xs" fw={600}>sessions</Text>
                    <Text size="xs" c="dimmed">Session data</Text>
                  </Box>
                </Stack>
              </Box>

              {/* Event Fields */}
              <Box className={classes.card}>
                <Box className={classes.cardHeader}>
                  <IconTable size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Event Fields</Text>
                </Box>
                <FieldsList fields={EVENTS_FIELDS} />
              </Box>
            </Stack>
          </Grid.Col>

          {/* Main - SQL Editor */}
          <Grid.Col span={{ base: 12, md: 6 }}>
            <SqlEditor
              value={sqlQuery}
              onChange={handleSqlChange}
              onValidate={handleValidateSql}
              isValidating={isValidatingSql}
              isValid={isSqlValid}
            />
          </Grid.Col>

          {/* Right Sidebar - Tips & Status */}
          <Grid.Col span={{ base: 12, md: 3 }}>
            <Stack gap="md">
              {/* SQL Tips */}
              <Box className={classes.tipsCard}>
                <Text className={classes.statsTitle}>SQL Tips</Text>
                <Stack gap={6}>
                  <Text size="xs" c="dimmed">
                    • Use <Text component="span" size="xs" c="teal" fw={500}>TIMESTAMP_SUB</Text> for time filters
                  </Text>
                  <Text size="xs" c="dimmed">
                    • Always include <Text component="span" size="xs" c="teal" fw={500}>LIMIT</Text> clause
                  </Text>
                  <Text size="xs" c="dimmed">
                    • Use <Text component="span" size="xs" c="teal" fw={500}>GROUP BY</Text> for aggregations
                  </Text>
                  <Text size="xs" c="dimmed">
                    • Column <Text component="span" size="xs" c="teal" fw={500}>props</Text> contains custom event data
                  </Text>
                </Stack>
              </Box>

              {/* Output Info */}
              <Box className={classes.card}>
                <Box className={classes.cardHeader}>
                  <IconTable size={16} className={classes.cardIcon} />
                  <Text className={classes.cardTitle}>Output Format</Text>
                </Box>
                <Text size="xs" c="dimmed">
                  SQL query results are displayed as a table. For visual charts, use the Visual Builder mode.
                </Text>
              </Box>

              {/* Query Status */}
              <Box className={classes.statsCard}>
                <Text className={classes.statsTitle}>Query Status</Text>
                <Box className={classes.statRow}>
                  <Text className={classes.statLabel}>Mode</Text>
                  <Badge size="xs" variant="light" color="violet">SQL</Badge>
                </Box>
                <Box className={classes.statRow}>
                  <Text className={classes.statLabel}>Valid</Text>
                  <Badge
                    size="xs"
                    variant="light"
                    color={isSqlValid === undefined ? "gray" : isSqlValid ? "green" : "red"}
                  >
                    {isSqlValid === undefined ? "Not checked" : isSqlValid ? "Yes" : "No"}
                  </Badge>
                </Box>
                <Box className={classes.statRow}>
                  <Text className={classes.statLabel}>Characters</Text>
                  <Text className={classes.statValue}>{sqlQuery.length}</Text>
                </Box>
              </Box>
            </Stack>
          </Grid.Col>
        </Grid>
      )}

        {/* Results Section */}
        <Box className={classes.resultsSection}>
          <h2 className={classes.sectionTitle}>
            Results
            {queryResult && (
              <Badge size="sm" variant="light" color="teal" ml="sm">
                {queryResult.totalRows.toLocaleString()} rows
              </Badge>
            )}
          </h2>
          <QueryResults
            data={queryResult}
            visualization={
              queryMode === "sql"
                ? { chartType: "table", showLegend: false }
                : queryState.visualization
            }
            isLoading={isRunning}
            error={queryError}
            onRefresh={handleRunQuery}
            onExport={handleExportCSV}
          />
        </Box>
      </Box>
    </Tabs>
  );
}
