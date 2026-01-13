/**
 * QueryBuilder Component - Enhanced UX Version
 * Visual query builder with improved usability and aesthetics
 */

import {
  Box,
  Stack,
  Paper,
  Group,
  Text,
  Button,
  NumberInput,
  Divider,
  Code,
  ScrollArea,
  Collapse,
  ActionIcon,
  Tooltip,
  Alert,
  Skeleton,
  Badge,
  Select,
  TextInput,
  SimpleGrid,
  Card,
  ThemeIcon,
  SegmentedControl,
  CopyButton,
} from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import { useDisclosure } from "@mantine/hooks";
import {
  IconCode,
  IconChevronDown,
  IconChevronUp,
  IconRefresh,
  IconAlertCircle,
  IconPlus,
  IconTrash,
  IconClock,
  IconChartBar,
  IconLayoutGrid,
  IconFilter,
  IconBraces,
  IconInfoCircle,
  IconTemplate,
  IconCalendar,
  IconTable,
  IconColumns,
  IconCheck,
  IconCopy,
  IconSparkles,
  IconGripVertical,
  IconDatabase,
  IconHash,
  IconLetterCase,
} from "@tabler/icons-react";
import { useState, useCallback, useEffect, useMemo } from "react";

import {
  QueryBuilderState,
  DEFAULT_QUERY_BUILDER_STATE,
  QueryBuilderProps,
  Metric,
  Dimension,
  Filter,
  TimeRangePreset,
  AggregationFunction,
  FilterOperator,
  AGGREGATION_OPTIONS,
  FILTER_OPERATORS,
  TIME_RANGE_PRESETS,
  QUERY_TEMPLATES,
  enhanceColumns,
  getAggregationsForColumn,
  SelectedColumn,
  QueryMode,
} from "./QueryBuilder.interface";
import { generateQuery, validateQueryBuilderState, generateQueryDescription } from "./utils/queryGenerator";

import classes from "./QueryBuilder.module.css";

// Generate unique IDs
const generateId = () => `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

// Popular columns to show as quick-add chips
const POPULAR_COLUMNS = ["service_name", "severity_text", "trace_id", "span_id", "body"];

export function QueryBuilder({
  tableName,
  databaseName,
  columns,
  onQueryChange,
  isLoading = false,
}: QueryBuilderProps) {
  // Enhanced columns with metadata
  const enhancedColumns = useMemo(() => enhanceColumns(columns), [columns]);
  
  // Query builder state
  const [state, setState] = useState<QueryBuilderState>(DEFAULT_QUERY_BUILDER_STATE);
  
  // UI state
  const [sqlPreviewOpen, { toggle: toggleSqlPreview }] = useDisclosure(true);
  const [showTemplates, setShowTemplates] = useState(false);
  
  // Validation errors
  const [validationErrors, setValidationErrors] = useState<string[]>([]);

  // Generate SQL query whenever state changes
  const generatedSql = useMemo(() => {
    if (!tableName || !databaseName) return "";
    return generateQuery(state, tableName, databaseName, enhancedColumns);
  }, [state, tableName, databaseName, enhancedColumns]);

  // Notify parent whenever generated SQL changes
  useEffect(() => {
    onQueryChange(generatedSql);
  }, [generatedSql, onQueryChange]);

  // Query description
  const queryDescription = useMemo(() => {
    return generateQueryDescription(state);
  }, [state]);

  // Validate state
  useEffect(() => {
    const errors = validateQueryBuilderState(state);
    setValidationErrors(errors);
  }, [state]);

  // Check if query is ready
  const isQueryReady = useMemo(() => {
    if (state.queryMode === "aggregate") {
      return state.metrics.length > 0 || state.dimensions.length > 0;
    }
    return true; // Select mode always has at least SELECT *
  }, [state.queryMode, state.metrics.length, state.dimensions.length]);

  // Column options for dropdowns
  const columnOptions = useMemo(() => {
    return enhancedColumns.map((col) => ({
      value: col.name,
      label: col.name,
      description: col.type,
    }));
  }, [enhancedColumns]);

  // Get JSON columns
  const jsonColumns = useMemo(() => {
    return enhancedColumns.filter((col) => col.isJson);
  }, [enhancedColumns]);

  // Get popular columns that exist
  const availablePopularColumns = useMemo(() => {
    return POPULAR_COLUMNS.filter(name => 
      enhancedColumns.some(col => col.name === name)
    );
  }, [enhancedColumns]);

  // Handlers
  const handleTimeRangeChange = useCallback((preset: string | null) => {
    if (!preset) return;
    setState((prev) => ({
      ...prev,
      timeRange: { 
        ...prev.timeRange, 
        preset: preset as TimeRangePreset,
        ...(preset !== "custom" ? { startDate: undefined, endDate: undefined } : {}),
      },
    }));
  }, []);

  const handleStartDateChange = useCallback((date: Date | null) => {
    setState((prev) => ({
      ...prev,
      timeRange: { ...prev.timeRange, startDate: date || undefined },
    }));
  }, []);

  const handleEndDateChange = useCallback((date: Date | null) => {
    setState((prev) => ({
      ...prev,
      timeRange: { ...prev.timeRange, endDate: date || undefined },
    }));
  }, []);

  // Metric handlers
  const handleAddMetric = useCallback((column?: string) => {
    const newMetric: Metric = {
      id: generateId(),
      column: column || "*",
      aggregation: "COUNT",
    };
    setState((prev) => ({ ...prev, metrics: [...prev.metrics, newMetric] }));
  }, []);

  const handleRemoveMetric = useCallback((id: string) => {
    setState((prev) => ({
      ...prev,
      metrics: prev.metrics.filter((m) => m.id !== id),
    }));
  }, []);

  const handleMetricChange = useCallback((id: string, updates: Partial<Metric>) => {
    setState((prev) => ({
      ...prev,
      metrics: prev.metrics.map((m) => (m.id === id ? { ...m, ...updates } : m)),
    }));
  }, []);

  // Dimension handlers
  const handleAddDimension = useCallback((column?: string) => {
    const newDimension: Dimension = {
      id: generateId(),
      column: column || enhancedColumns[0]?.name || "",
    };
    setState((prev) => ({ ...prev, dimensions: [...prev.dimensions, newDimension] }));
  }, [enhancedColumns]);

  const handleRemoveDimension = useCallback((id: string) => {
    setState((prev) => ({
      ...prev,
      dimensions: prev.dimensions.filter((d) => d.id !== id),
    }));
  }, []);

  const handleDimensionChange = useCallback((id: string, updates: Partial<Dimension>) => {
    setState((prev) => ({
      ...prev,
      dimensions: prev.dimensions.map((d) => (d.id === id ? { ...d, ...updates } : d)),
    }));
  }, []);

  // Filter handlers
  const handleAddFilter = useCallback((column?: string) => {
    const newFilter: Filter = {
      id: generateId(),
      column: column || enhancedColumns[0]?.name || "",
      operator: "equals",
      value: "",
    };
    setState((prev) => ({ ...prev, filters: [...prev.filters, newFilter] }));
  }, [enhancedColumns]);

  const handleRemoveFilter = useCallback((id: string) => {
    setState((prev) => ({
      ...prev,
      filters: prev.filters.filter((f) => f.id !== id),
    }));
  }, []);

  const handleFilterChange = useCallback((id: string, updates: Partial<Filter>) => {
    setState((prev) => ({
      ...prev,
      filters: prev.filters.map((f) => (f.id === id ? { ...f, ...updates } : f)),
    }));
  }, []);

  // Query mode handler
  const handleQueryModeChange = useCallback((mode: string) => {
    setState((prev) => ({
      ...prev,
      queryMode: mode as QueryMode,
    }));
  }, []);

  // Selected columns handlers (for select mode)
  const handleAddSelectedColumn = useCallback((column?: string) => {
    const newCol: SelectedColumn = {
      id: generateId(),
      column: column || enhancedColumns[0]?.name || "",
    };
    setState((prev) => ({ ...prev, selectedColumns: [...prev.selectedColumns, newCol] }));
  }, [enhancedColumns]);

  const handleRemoveSelectedColumn = useCallback((id: string) => {
    setState((prev) => ({
      ...prev,
      selectedColumns: prev.selectedColumns.filter((c) => c.id !== id),
    }));
  }, []);

  const handleSelectedColumnChange = useCallback((id: string, updates: Partial<SelectedColumn>) => {
    setState((prev) => ({
      ...prev,
      selectedColumns: prev.selectedColumns.map((c) => (c.id === id ? { ...c, ...updates } : c)),
    }));
  }, []);

  const handleLimitChange = useCallback((value: string | number) => {
    const limit = typeof value === "string" ? parseInt(value, 10) : value;
    if (!isNaN(limit) && limit > 0 && limit <= 10000) {
      setState((prev) => ({ ...prev, limit }));
    }
  }, []);

  const handleReset = useCallback(() => {
    setState(DEFAULT_QUERY_BUILDER_STATE);
  }, []);

  const handleApplyTemplate = useCallback((templateId: string) => {
    const template = QUERY_TEMPLATES.find((t) => t.id === templateId);
    if (template && template.state) {
      setState((prev) => ({
        ...prev,
        ...template.state,
        metrics: template.state.metrics?.map((m) => ({ ...m, id: generateId() })) || prev.metrics,
        dimensions: template.state.dimensions?.map((d) => ({ ...d, id: generateId() })) || prev.dimensions,
        filters: template.state.filters?.map((f) => ({ ...f, id: generateId() })) || prev.filters,
      }));
      setShowTemplates(false);
    }
  }, []);

  // Get column type icon
  const getColumnTypeIcon = (type: string) => {
    const lowerType = type.toLowerCase();
    if (lowerType.includes("int") || lowerType.includes("double") || lowerType.includes("decimal")) {
      return <IconHash size={12} />;
    }
    if (lowerType.includes("json") || lowerType.includes("map") || lowerType.includes("array")) {
      return <IconBraces size={12} />;
    }
    return <IconLetterCase size={12} />;
  };

  // Render loading skeleton
  if (isLoading) {
    return (
      <Paper className={classes.container} withBorder>
        <Box className={classes.header}>
          <Skeleton height={24} width={200} />
        </Box>
        <Stack gap="md" p="md">
          <Skeleton height={100} radius="md" />
          <Skeleton height={100} radius="md" />
          <Skeleton height={100} radius="md" />
        </Stack>
      </Paper>
    );
  }

  // Render empty state if no columns
  if (enhancedColumns.length === 0) {
    return (
      <Paper className={classes.container} withBorder>
        <Box className={classes.header}>
          <Group gap="xs">
            <IconDatabase size={18} />
          <Text size="sm" fw={600}>Query Builder</Text>
          </Group>
        </Box>
        <Box p="xl">
          <Alert icon={<IconAlertCircle size={16} />} color="orange" variant="light" radius="md">
            <Text size="sm">No columns available. Please check if metadata is loaded correctly.</Text>
          </Alert>
        </Box>
      </Paper>
    );
  }

  return (
    <Paper className={classes.container} withBorder>
      {/* Header */}
      <Box className={classes.header}>
        <Group justify="space-between">
          <Group gap="sm">
            <ThemeIcon size="sm" variant="gradient" gradient={{ from: "teal", to: "cyan" }} radius="md">
              <IconDatabase size={14} />
            </ThemeIcon>
            <Text size="sm" fw={600}>Query Builder</Text>
            <Badge size="xs" variant="light" color="teal" radius="sm">
              {enhancedColumns.length} columns
            </Badge>
            {jsonColumns.length > 0 && (
              <Badge size="xs" variant="light" color="violet" radius="sm">
                {jsonColumns.length} JSON
              </Badge>
            )}
          </Group>
          <Group gap={6}>
            <Tooltip label="Quick Templates" position="bottom" withArrow>
              <ActionIcon 
                variant={showTemplates ? "gradient" : "subtle"} 
                gradient={{ from: "teal", to: "cyan" }}
                size="sm" 
                radius="md"
                onClick={() => setShowTemplates(!showTemplates)}
              >
                <IconTemplate size={14} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label="Reset all" position="bottom" withArrow>
              <ActionIcon variant="subtle" size="sm" radius="md" onClick={handleReset} color="gray">
                <IconRefresh size={14} />
              </ActionIcon>
            </Tooltip>
          </Group>
        </Group>
      </Box>

      {/* Templates */}
      <Collapse in={showTemplates}>
        <Box className={classes.templatesSection}>
          <Group gap="xs" mb="sm">
            <IconSparkles size={14} color="var(--mantine-color-teal-6)" />
            <Text size="xs" fw={600}>Quick Start Templates</Text>
          </Group>
          <SimpleGrid cols={2} spacing="xs">
            {QUERY_TEMPLATES.map((template) => (
              <Card
                key={template.id}
                className={classes.templateCard}
                padding="sm"
                withBorder
                onClick={() => handleApplyTemplate(template.id)}
              >
                <Text size="xs" fw={600}>{template.name}</Text>
                <Text size="xs" c="dimmed" lineClamp={1}>{template.description}</Text>
              </Card>
            ))}
          </SimpleGrid>
        </Box>
      </Collapse>

      {/* Query Mode Toggle */}
      <Box className={classes.queryModeSection}>
        <Group gap="xs" mb={8}>
          <Text size="xs" fw={600} c="dimmed">Query Type</Text>
        </Group>
        <SegmentedControl
          value={state.queryMode}
          onChange={handleQueryModeChange}
          size="xs"
          fullWidth
          radius="md"
          data={[
            {
              value: "aggregate",
              label: (
                <Group gap={6} justify="center">
                  <IconChartBar size={14} />
                  <span>Aggregate</span>
                </Group>
              ),
            },
            {
              value: "select",
              label: (
                <Group gap={6} justify="center">
                  <IconTable size={14} />
                  <span>Select Rows</span>
                </Group>
              ),
            },
          ]}
        />
        <Text size="xs" c="dimmed" mt={6}>
          {state.queryMode === "aggregate" 
            ? "Calculate metrics like COUNT, SUM, AVG with optional grouping" 
            : "Select specific columns from rows without aggregation"}
        </Text>
      </Box>

      {/* Query Description Bar */}
      <Box className={classes.descriptionBar}>
        <Box 
          className={classes.statusIndicator}
          style={{ 
            background: isQueryReady ? "#12b886" : "#fab005",
            boxShadow: isQueryReady 
              ? "0 0 8px rgba(18, 184, 134, 0.4)" 
              : "0 0 8px rgba(250, 176, 5, 0.4)"
          }}
        />
        <Text size="xs" c="dimmed" style={{ flex: 1 }} lineClamp={1}>
          {queryDescription}
        </Text>
        {isQueryReady && (
          <Badge size="xs" variant="light" color="teal">
            Ready
          </Badge>
        )}
      </Box>

      {/* Builder Content */}
      <ScrollArea h={320} type="auto" offsetScrollbars scrollbarSize={6}>
        <Stack gap="md" p="md">
          {/* Validation Errors */}
          {validationErrors.length > 0 && (
            <Alert
              icon={<IconAlertCircle size={16} />}
              color="red"
              variant="light"
              title="Please fix these issues"
              radius="md"
            >
              <Stack gap={4}>
                {validationErrors.map((error, i) => (
                  <Text key={i} size="xs">{error}</Text>
                ))}
              </Stack>
            </Alert>
          )}

          {/* TIME RANGE SECTION */}
          <Box className={classes.section} data-section="time">
            <Group gap="xs" mb="sm">
              <ThemeIcon size="sm" variant="light" color="blue" radius="md">
                <IconClock size={14} />
              </ThemeIcon>
              <Text size="sm" fw={600}>Time Range</Text>
              <Badge size="xs" color="red" variant="light" radius="sm">Required</Badge>
            </Group>
            <Select
              placeholder="Select time range"
              data={TIME_RANGE_PRESETS.map((p) => ({ value: p.value, label: p.label }))}
              value={state.timeRange.preset}
              onChange={handleTimeRangeChange}
              leftSection={<IconCalendar size={14} />}
              size="sm"
              radius="md"
              mb={state.timeRange.preset === "custom" ? "sm" : 0}
            />
            
            {/* Custom Date/Time Pickers */}
            <Collapse in={state.timeRange.preset === "custom"}>
              <Stack gap="xs" mt="sm">
                <Group grow>
                  <DateTimePicker
                    label={
                      <Group gap={4}>
                        <Text size="xs">Start</Text>
                        <Text size="xs" c="dimmed">(Local time)</Text>
                      </Group>
                    }
                    placeholder="Select start"
                    value={state.timeRange.startDate || null}
                    onChange={handleStartDateChange}
                    size="xs"
                    maxDate={state.timeRange.endDate || new Date()}
                    clearable
                    valueFormat="MMM D, YYYY HH:mm"
                  />
                  <DateTimePicker
                    label={
                      <Group gap={4}>
                        <Text size="xs">End</Text>
                        <Text size="xs" c="dimmed">(Local time)</Text>
                      </Group>
                    }
                    placeholder="Select end"
                    value={state.timeRange.endDate || null}
                    onChange={handleEndDateChange}
                    size="xs"
                    minDate={state.timeRange.startDate || undefined}
                    maxDate={new Date()}
                    clearable
                    valueFormat="MMM D, YYYY HH:mm"
                  />
                </Group>
                <Group gap={4}>
                  <IconInfoCircle size={12} color="var(--mantine-color-dimmed)" />
                  <Text size="xs" c="dimmed">
                    Times will be converted to UTC for querying
                  </Text>
                </Group>
              </Stack>
            </Collapse>
          </Box>

          {/* SELECT MODE - SELECTED COLUMNS SECTION */}
          {state.queryMode === "select" && (
              <Box className={classes.section} data-section="columns">
                <Group justify="space-between" mb="sm">
                  <Group gap="xs">
                    <ThemeIcon size="sm" variant="light" color="cyan" radius="md">
                      <IconColumns size={14} />
                    </ThemeIcon>
                    <Text size="sm" fw={600}>Columns</Text>
                    <Badge size="xs" variant="light" radius="sm">
                      {state.selectedColumns.length || "All"}
                    </Badge>
                  </Group>
                  <Button
                    className={classes.addButton}
                    variant="light"
                    size="xs"
                    color="cyan"
                    leftSection={<IconPlus size={12} />}
                    radius="md"
                    onClick={() => handleAddSelectedColumn()}
                  >
                    Add Column
                  </Button>
                </Group>
                
                {state.selectedColumns.length === 0 ? (
                  <Box className={classes.emptyState}>
                    <Box className={classes.emptyStateIcon}>
                      <IconColumns size={20} color="var(--mantine-color-cyan-6)" />
                    </Box>
                    <Text size="xs" c="dimmed" ta="center">
                      No columns selected — using <Code>SELECT *</Code>
                    </Text>
                    <Text size="xs" c="dimmed" ta="center" mt={4}>
                      Add columns for better performance
                    </Text>
                    
                    {/* Quick add chips */}
                    {availablePopularColumns.length > 0 && (
                      <Box className={classes.quickAddContainer}>
                        <Text size="xs" c="dimmed" mr={4}>Quick add:</Text>
                        {availablePopularColumns.slice(0, 4).map((col) => (
                          <Box 
                            key={col} 
                            className={classes.quickAddChip}
                            onClick={() => handleAddSelectedColumn(col)}
                          >
                            <IconPlus size={10} />
                            {col}
                          </Box>
                        ))}
                      </Box>
                    )}
                  </Box>
                ) : (
                  <Stack gap="xs">
                    {state.selectedColumns.map((col, index) => (
                      <Box key={col.id} className={`${classes.itemRow} ${classes.fadeIn}`}>
                        <Group gap="xs" wrap="nowrap">
                          <Box className={classes.dragHandle}>
                            <IconGripVertical size={14} color="var(--mantine-color-dimmed)" />
                          </Box>
                          <Badge size="xs" variant="light" color="gray" circle>
                            {index + 1}
                          </Badge>
                          <Select
                            placeholder="Select column"
                            data={columnOptions}
                            value={col.column}
                            onChange={(v) => handleSelectedColumnChange(col.id, { column: v || "" })}
                            size="xs"
                            radius="md"
                            style={{ flex: 1 }}
                            searchable
                            leftSection={getColumnTypeIcon(enhancedColumns.find(c => c.name === col.column)?.type || "")}
                          />
                          {enhancedColumns.find((c) => c.name === col.column)?.isJson && (
                            <TextInput
                              placeholder="$.path"
                              value={col.jsonPath || ""}
                              onChange={(e) => handleSelectedColumnChange(col.id, { jsonPath: e.target.value })}
                              size="xs"
                              radius="md"
                              w={100}
                              leftSection={<IconBraces size={12} />}
                            />
                          )}
                          <TextInput
                            placeholder="Alias"
                            value={col.alias || ""}
                            onChange={(e) => handleSelectedColumnChange(col.id, { alias: e.target.value })}
                            size="xs"
                            radius="md"
                            w={80}
                          />
                          <Tooltip label="Remove" position="left" withArrow>
                            <ActionIcon
                              variant="subtle"
                              color="red"
                              size="sm"
                              radius="md"
                              onClick={() => handleRemoveSelectedColumn(col.id)}
                            >
                              <IconTrash size={14} />
                            </ActionIcon>
                          </Tooltip>
                        </Group>
                      </Box>
                    ))}
                  </Stack>
                )}
              </Box>
          )}

          {/* AGGREGATE MODE - METRICS SECTION */}
          {state.queryMode === "aggregate" && (
          <Box className={classes.section} data-section="metrics">
            <Group justify="space-between" mb="sm">
              <Group gap="xs">
                    <ThemeIcon size="sm" variant="light" color="teal" radius="md">
                  <IconChartBar size={14} />
                </ThemeIcon>
                    <Text size="sm" fw={600}>Metrics</Text>
                    <Badge size="xs" variant="light" radius="sm">{state.metrics.length}</Badge>
              </Group>
              <Button
                    className={classes.addButton}
                variant="light"
                size="xs"
                leftSection={<IconPlus size={12} />}
                    radius="md"
                    onClick={() => handleAddMetric()}
              >
                    Add Metric
              </Button>
            </Group>
            
            {state.metrics.length === 0 ? (
              <Box className={classes.emptyState}>
                    <Box className={classes.emptyStateIcon}>
                      <IconChartBar size={20} color="var(--mantine-color-teal-6)" />
                    </Box>
                    <Text size="xs" c="dimmed" ta="center">
                      No metrics yet
                </Text>
                    <Text size="xs" c="dimmed" ta="center" mt={4}>
                      Add COUNT, SUM, AVG, or other aggregations
                    </Text>
                    
                    {/* Quick add chips */}
                    <Box className={classes.quickAddContainer}>
                      <Text size="xs" c="dimmed" mr={4}>Quick add:</Text>
                      <Box 
                        className={classes.quickAddChip}
                        onClick={() => handleAddMetric("*")}
                      >
                        <IconPlus size={10} />
                        COUNT(*)
                      </Box>
                    </Box>
              </Box>
            ) : (
              <Stack gap="xs">
                    {state.metrics.map((metric, index) => {
                      const selectedColumn = metric.column === "*" ? undefined : enhancedColumns.find((c) => c.name === metric.column);
                      const validAggregations = getAggregationsForColumn(selectedColumn);
                      const isCurrentAggValid = validAggregations.includes(metric.aggregation);
                      
                      return (
                        <Box key={metric.id} className={`${classes.itemRow} ${classes.fadeIn}`}>
                    <Group gap="xs" wrap="nowrap">
                            <Box className={classes.dragHandle}>
                              <IconGripVertical size={14} color="var(--mantine-color-dimmed)" />
                            </Box>
                            <Badge size="xs" variant="light" color="gray" circle>
                              {index + 1}
                            </Badge>
                            <Tooltip 
                              label={!isCurrentAggValid ? `Will cast to numeric type` : undefined}
                              disabled={isCurrentAggValid}
                              color="orange"
                              position="top"
                              withArrow
                            >
                      <Select
                        placeholder="Function"
                                data={AGGREGATION_OPTIONS.map((o) => ({ 
                                  value: o.value, 
                                  label: o.label,
                                }))}
                        value={metric.aggregation}
                        onChange={(v) => handleMetricChange(metric.id, { aggregation: v as AggregationFunction })}
                        size="xs"
                                radius="md"
                                w={100}
                                styles={!isCurrentAggValid ? { 
                                  input: { borderColor: "var(--mantine-color-orange-5)" } 
                                } : undefined}
                              />
                            </Tooltip>
                      <Select
                              placeholder="Column"
                        data={[
                          { value: "*", label: "All rows (*)" },
                          ...columnOptions,
                        ]}
                        value={metric.column}
                              onChange={(v) => {
                                const newColumn = v === "*" ? undefined : enhancedColumns.find(c => c.name === v);
                                const newValidAggs = getAggregationsForColumn(newColumn);
                                const updates: Partial<Metric> = { column: v || "*" };
                                
                                if (!newValidAggs.includes(metric.aggregation)) {
                                  updates.aggregation = "COUNT";
                                }
                                
                                handleMetricChange(metric.id, updates);
                              }}
                        size="xs"
                              radius="md"
                        style={{ flex: 1 }}
                        searchable
                              leftSection={metric.column !== "*" ? getColumnTypeIcon(selectedColumn?.type || "") : undefined}
                      />
                            {selectedColumn?.isJson && (
                        <TextInput
                                placeholder="$.path"
                          value={metric.jsonPath || ""}
                          onChange={(e) => handleMetricChange(metric.id, { jsonPath: e.target.value })}
                          size="xs"
                                radius="md"
                                w={100}
                          leftSection={<IconBraces size={12} />}
                        />
                      )}
                            <Tooltip label="Remove" position="left" withArrow>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        size="sm"
                                radius="md"
                        onClick={() => handleRemoveMetric(metric.id)}
                      >
                        <IconTrash size={14} />
                      </ActionIcon>
                            </Tooltip>
                    </Group>
                  </Box>
                      );
                    })}
              </Stack>
            )}
          </Box>
          )}

          {/* AGGREGATE MODE - DIMENSIONS SECTION */}
          {state.queryMode === "aggregate" && (
          <Box className={classes.section} data-section="dimensions">
            <Group justify="space-between" mb="sm">
              <Group gap="xs">
                    <ThemeIcon size="sm" variant="light" color="violet" radius="md">
                  <IconLayoutGrid size={14} />
                </ThemeIcon>
                    <Text size="sm" fw={600}>Group By</Text>
                    <Badge size="xs" variant="light" radius="sm">{state.dimensions.length}</Badge>
              </Group>
              <Button
                    className={classes.addButton}
                variant="light"
                size="xs"
                color="violet"
                leftSection={<IconPlus size={12} />}
                    radius="md"
                    onClick={() => handleAddDimension()}
              >
                    Add Grouping
              </Button>
            </Group>
            
            {state.dimensions.length === 0 ? (
              <Box className={classes.emptyState}>
                    <Box className={classes.emptyStateIcon}>
                      <IconLayoutGrid size={20} color="var(--mantine-color-violet-6)" />
                    </Box>
                    <Text size="xs" c="dimmed" ta="center">
                      No grouping — single aggregated row
                </Text>
                    
                    {/* Quick add chips */}
                    {availablePopularColumns.length > 0 && (
                      <Box className={classes.quickAddContainer}>
                        <Text size="xs" c="dimmed" mr={4}>Group by:</Text>
                        {availablePopularColumns.slice(0, 3).map((col) => (
                          <Box 
                            key={col} 
                            className={classes.quickAddChip}
                            onClick={() => handleAddDimension(col)}
                          >
                            <IconPlus size={10} />
                            {col}
                          </Box>
                        ))}
                      </Box>
                    )}
              </Box>
            ) : (
              <Stack gap="xs">
                    {state.dimensions.map((dimension, index) => (
                      <Box key={dimension.id} className={`${classes.itemRow} ${classes.fadeIn}`}>
                    <Group gap="xs" wrap="nowrap">
                          <Box className={classes.dragHandle}>
                            <IconGripVertical size={14} color="var(--mantine-color-dimmed)" />
                          </Box>
                          <Badge size="xs" variant="light" color="gray" circle>
                            {index + 1}
                          </Badge>
                      <Select
                        placeholder="Select column"
                        data={columnOptions}
                        value={dimension.column}
                        onChange={(v) => handleDimensionChange(dimension.id, { column: v || "" })}
                        size="xs"
                            radius="md"
                        style={{ flex: 1 }}
                        searchable
                            leftSection={getColumnTypeIcon(enhancedColumns.find(c => c.name === dimension.column)?.type || "")}
                      />
                      {enhancedColumns.find((c) => c.name === dimension.column)?.isJson && (
                        <TextInput
                              placeholder="$.path"
                          value={dimension.jsonPath || ""}
                          onChange={(e) => handleDimensionChange(dimension.id, { jsonPath: e.target.value })}
                          size="xs"
                              radius="md"
                              w={100}
                          leftSection={<IconBraces size={12} />}
                        />
                      )}
                          <Tooltip label="Remove" position="left" withArrow>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        size="sm"
                              radius="md"
                        onClick={() => handleRemoveDimension(dimension.id)}
                      >
                        <IconTrash size={14} />
                      </ActionIcon>
                          </Tooltip>
                    </Group>
                  </Box>
                ))}
              </Stack>
            )}
          </Box>
          )}

          {/* FILTERS SECTION */}
          <Box className={classes.section} data-section="filters">
            <Group justify="space-between" mb="sm">
              <Group gap="xs">
                <ThemeIcon size="sm" variant="light" color="orange" radius="md">
                  <IconFilter size={14} />
                </ThemeIcon>
                <Text size="sm" fw={600}>Filters</Text>
                <Badge size="xs" variant="light" radius="sm">{state.filters.length}</Badge>
              </Group>
              <Button
                className={classes.addButton}
                variant="light"
                size="xs"
                color="orange"
                leftSection={<IconPlus size={12} />}
                radius="md"
                onClick={() => handleAddFilter()}
              >
                Add Filter
              </Button>
            </Group>
            
            {state.filters.length === 0 ? (
              <Box className={classes.emptyState}>
                <Box className={classes.emptyStateIcon}>
                  <IconFilter size={20} color="var(--mantine-color-orange-6)" />
                </Box>
                <Text size="xs" c="dimmed" ta="center">
                  No filters — all data in time range included
                </Text>
                
                {/* Quick add chips */}
                {availablePopularColumns.length > 0 && (
                  <Box className={classes.quickAddContainer}>
                    <Text size="xs" c="dimmed" mr={4}>Filter by:</Text>
                    {availablePopularColumns.slice(0, 3).map((col) => (
                      <Box 
                        key={col} 
                        className={classes.quickAddChip}
                        onClick={() => handleAddFilter(col)}
                      >
                        <IconPlus size={10} />
                        {col}
                      </Box>
                    ))}
                  </Box>
                )}
              </Box>
            ) : (
              <Stack gap="xs">
                {state.filters.map((filter, index) => {
                  const operatorConfig = FILTER_OPERATORS.find((o) => o.value === filter.operator);
                  const needsValue = operatorConfig?.requiresValue !== false;
                  
                  return (
                    <Box key={filter.id} className={`${classes.itemRow} ${classes.fadeIn}`}>
                      <Group gap={4} mb={6}>
                        <Badge size="xs" variant="light" color="orange">
                          {index === 0 ? "WHERE" : "AND"}
                        </Badge>
                      </Group>
                      <Group gap="xs" wrap="nowrap">
                        <Select
                          placeholder="Column"
                          data={columnOptions}
                          value={filter.column}
                          onChange={(v) => handleFilterChange(filter.id, { column: v || "" })}
                          size="xs"
                          radius="md"
                          w={130}
                          searchable
                          leftSection={getColumnTypeIcon(enhancedColumns.find(c => c.name === filter.column)?.type || "")}
                        />
                        {enhancedColumns.find((c) => c.name === filter.column)?.isJson && (
                          <TextInput
                            placeholder="$.path"
                            value={filter.jsonPath || ""}
                            onChange={(e) => handleFilterChange(filter.id, { jsonPath: e.target.value })}
                            size="xs"
                            radius="md"
                            w={70}
                            leftSection={<IconBraces size={10} />}
                          />
                        )}
                        <Select
                          placeholder="Operator"
                          data={FILTER_OPERATORS.map((o) => ({ value: o.value, label: o.label }))}
                          value={filter.operator}
                          onChange={(v) => handleFilterChange(filter.id, { operator: (v || "equals") as FilterOperator })}
                          size="xs"
                          radius="md"
                          w={110}
                        />
                        {needsValue && (
                          <TextInput
                            placeholder={operatorConfig?.isArrayValue ? "val1, val2, ..." : "Value"}
                            value={Array.isArray(filter.value) ? filter.value.join(", ") : filter.value}
                            onChange={(e) => {
                              const val = e.target.value;
                              if (operatorConfig?.isArrayValue) {
                                handleFilterChange(filter.id, { value: val.split(",").map((v) => v.trim()) });
                              } else {
                                handleFilterChange(filter.id, { value: val });
                              }
                            }}
                            size="xs"
                            radius="md"
                            style={{ flex: 1 }}
                          />
                        )}
                        <Tooltip label="Remove" position="left" withArrow>
                        <ActionIcon
                          variant="subtle"
                          color="red"
                          size="sm"
                            radius="md"
                          onClick={() => handleRemoveFilter(filter.id)}
                        >
                          <IconTrash size={14} />
                        </ActionIcon>
                        </Tooltip>
                      </Group>
                    </Box>
                  );
                })}
              </Stack>
            )}
          </Box>

          {/* LIMIT SECTION */}
          <Box className={classes.section} data-section="limit">
            <Group gap="sm" align="center">
              <Text size="sm" fw={500}>Limit</Text>
              <NumberInput
                value={state.limit}
                onChange={handleLimitChange}
                min={1}
                max={10000}
                step={100}
                size="xs"
                radius="md"
                w={100}
              />
              <Text size="xs" c="dimmed">rows</Text>
              <Text size="xs" c="dimmed" ml="auto">max 10,000</Text>
            </Group>
          </Box>
        </Stack>
      </ScrollArea>

      <Divider color="rgba(14, 201, 194, 0.1)" />

      {/* SQL Preview */}
      <Box className={`${classes.sqlPreview} ${sqlPreviewOpen ? classes.sqlPreviewOpen : ""}`}>
        <Group
          justify="space-between"
          className={classes.sqlPreviewHeader}
          onClick={toggleSqlPreview}
          style={{ cursor: "pointer" }}
        >
          <Group gap="xs">
            <IconCode size={14} />
            <Text size="xs" fw={600}>Generated SQL</Text>
            {generatedSql && (
              <Badge size="xs" variant="light" color="teal">Ready</Badge>
            )}
          </Group>
          <Group gap="xs">
            {generatedSql && (
              <CopyButton value={generatedSql}>
                {({ copied, copy }) => (
                  <Tooltip label={copied ? "Copied!" : "Copy SQL"} position="left" withArrow>
                    <ActionIcon 
                      variant="subtle" 
                      size="xs" 
                      color={copied ? "teal" : "gray"}
                      onClick={(e) => { e.stopPropagation(); copy(); }}
                    >
                      {copied ? <IconCheck size={12} /> : <IconCopy size={12} />}
                    </ActionIcon>
                  </Tooltip>
                )}
              </CopyButton>
            )}
          <ActionIcon variant="subtle" size="xs">
            {sqlPreviewOpen ? <IconChevronDown size={14} /> : <IconChevronUp size={14} />}
          </ActionIcon>
          </Group>
        </Group>
        <Collapse in={sqlPreviewOpen}>
          <ScrollArea h={100} className={classes.sqlPreviewCode}>
            <Code block className={classes.sqlCode}>
              {generatedSql || (state.queryMode === "aggregate" 
                ? "-- Add metrics to generate a query" 
                : "-- Query will select all columns or add specific columns")}
            </Code>
          </ScrollArea>
        </Collapse>
      </Box>
    </Paper>
  );
}
