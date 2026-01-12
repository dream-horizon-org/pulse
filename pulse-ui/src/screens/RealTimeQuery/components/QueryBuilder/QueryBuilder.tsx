/**
 * QueryBuilder Component - Improved Version
 * Visual query builder for aggregated queries with JSON support
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
} from "@mantine/core";
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
} from "./QueryBuilder.interface";
import { generateQuery, validateQueryBuilderState, generateQueryDescription } from "./utils/queryGenerator";

import classes from "./QueryBuilder.module.css";

// Generate unique IDs
const generateId = () => `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

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
    return generateQuery(state, tableName, databaseName);
  }, [state, tableName, databaseName]);

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

  // Column options for dropdowns
  const columnOptions = useMemo(() => {
    return enhancedColumns.map((col) => ({
      value: col.name,
      label: `${col.name}`,
      description: col.type,
    }));
  }, [enhancedColumns]);

  // Get JSON columns
  const jsonColumns = useMemo(() => {
    return enhancedColumns.filter((col) => col.isJson);
  }, [enhancedColumns]);

  // Handlers
  const handleTimeRangeChange = useCallback((preset: string | null) => {
    if (!preset) return;
    setState((prev) => ({
      ...prev,
      timeRange: { ...prev.timeRange, preset: preset as TimeRangePreset },
    }));
  }, []);

  // Metric handlers
  const handleAddMetric = useCallback(() => {
    const newMetric: Metric = {
      id: generateId(),
      column: "*",
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
  const handleAddDimension = useCallback(() => {
    const newDimension: Dimension = {
      id: generateId(),
      column: enhancedColumns[0]?.name || "",
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
  const handleAddFilter = useCallback(() => {
    const newFilter: Filter = {
      id: generateId(),
      column: enhancedColumns[0]?.name || "",
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

  // Render loading skeleton
  if (isLoading) {
    return (
      <Paper className={classes.container} withBorder>
        <Box className={classes.header}>
          <Skeleton height={24} width={200} />
        </Box>
        <Stack gap="md" p="md">
          <Skeleton height={100} />
          <Skeleton height={100} />
          <Skeleton height={100} />
        </Stack>
      </Paper>
    );
  }

  // Render column info if no columns
  if (enhancedColumns.length === 0) {
    return (
      <Paper className={classes.container} withBorder>
        <Box className={classes.header}>
          <Text size="sm" fw={600}>Query Builder</Text>
        </Box>
        <Box p="xl">
          <Alert icon={<IconAlertCircle size={16} />} color="orange" variant="light">
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
          <Group gap="xs">
            <Text size="sm" fw={600}>Query Builder</Text>
            <Badge size="xs" variant="light" color="teal">
              {enhancedColumns.length} columns
            </Badge>
            {jsonColumns.length > 0 && (
              <Badge size="xs" variant="light" color="violet">
                {jsonColumns.length} JSON
              </Badge>
            )}
          </Group>
          <Group gap="xs">
            <Tooltip label="Quick Templates">
              <ActionIcon 
                variant={showTemplates ? "filled" : "subtle"} 
                size="sm" 
                color="teal"
                onClick={() => setShowTemplates(!showTemplates)}
              >
                <IconTemplate size={14} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label="Reset all">
              <ActionIcon variant="subtle" size="sm" onClick={handleReset}>
                <IconRefresh size={14} />
              </ActionIcon>
            </Tooltip>
          </Group>
        </Group>
      </Box>

      {/* Templates */}
      <Collapse in={showTemplates}>
        <Box className={classes.templatesSection}>
          <Text size="xs" fw={600} mb="xs">Quick Start Templates</Text>
          <SimpleGrid cols={2} spacing="xs">
            {QUERY_TEMPLATES.map((template) => (
              <Card
                key={template.id}
                className={classes.templateCard}
                padding="xs"
                withBorder
                onClick={() => handleApplyTemplate(template.id)}
              >
                <Text size="xs" fw={500}>{template.name}</Text>
                <Text size="xs" c="dimmed">{template.description}</Text>
              </Card>
            ))}
          </SimpleGrid>
        </Box>
      </Collapse>

      {/* Query Description */}
      <Box className={classes.descriptionBar}>
        <Text size="xs" c="dimmed" truncate>
          {queryDescription}
        </Text>
      </Box>

      {/* Builder Content */}
      <ScrollArea h={350} type="auto" offsetScrollbars scrollbarSize={8}>
        <Stack gap="md" p="md">
          {/* Validation Errors */}
          {validationErrors.length > 0 && (
            <Alert
              icon={<IconAlertCircle size={16} />}
              color="red"
              variant="light"
              title="Please fix these issues"
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
              <ThemeIcon size="sm" variant="light" color="blue">
                <IconClock size={14} />
              </ThemeIcon>
              <Text size="sm" fw={600}>Time Range</Text>
              <Badge size="xs" color="red" variant="light">Required</Badge>
            </Group>
            <Select
              placeholder="Select time range"
              data={TIME_RANGE_PRESETS.map((p) => ({ value: p.value, label: p.label }))}
              value={state.timeRange.preset}
              onChange={handleTimeRangeChange}
              leftSection={<IconCalendar size={14} />}
              size="sm"
            />
          </Box>

          {/* METRICS SECTION */}
          <Box className={classes.section} data-section="metrics">
            <Group justify="space-between" mb="sm">
              <Group gap="xs">
                <ThemeIcon size="sm" variant="light" color="teal">
                  <IconChartBar size={14} />
                </ThemeIcon>
                <Text size="sm" fw={600}>Metrics (What to Calculate)</Text>
                <Badge size="xs" variant="light">{state.metrics.length}</Badge>
              </Group>
              <Button
                variant="light"
                size="xs"
                leftSection={<IconPlus size={12} />}
                onClick={handleAddMetric}
              >
                Add
              </Button>
            </Group>
            
            {state.metrics.length === 0 ? (
              <Box className={classes.emptyState}>
                <IconInfoCircle size={20} color="gray" />
                <Text size="xs" c="dimmed" ta="center" mt="xs">
                  No metrics yet. Add a metric to calculate values like COUNT, SUM, or AVG.
                </Text>
              </Box>
            ) : (
              <Stack gap="xs">
                {state.metrics.map((metric) => (
                  <Box key={metric.id} className={classes.itemRow}>
                    <Group gap="xs" wrap="nowrap">
                      <Select
                        placeholder="Function"
                        data={AGGREGATION_OPTIONS.map((o) => ({ value: o.value, label: o.label }))}
                        value={metric.aggregation}
                        onChange={(v) => handleMetricChange(metric.id, { aggregation: v as AggregationFunction })}
                        size="xs"
                        w={110}
                      />
                      <Select
                        placeholder="Select column"
                        data={[
                          { value: "*", label: "All rows (*)" },
                          ...columnOptions,
                        ]}
                        value={metric.column}
                        onChange={(v) => handleMetricChange(metric.id, { column: v || "*" })}
                        size="xs"
                        style={{ flex: 1 }}
                        searchable
                      />
                      {enhancedColumns.find((c) => c.name === metric.column)?.isJson && (
                        <TextInput
                          placeholder="$.field.path"
                          value={metric.jsonPath || ""}
                          onChange={(e) => handleMetricChange(metric.id, { jsonPath: e.target.value })}
                          size="xs"
                          w={120}
                          leftSection={<IconBraces size={12} />}
                        />
                      )}
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        size="sm"
                        onClick={() => handleRemoveMetric(metric.id)}
                      >
                        <IconTrash size={14} />
                      </ActionIcon>
                    </Group>
                  </Box>
                ))}
              </Stack>
            )}
          </Box>

          {/* DIMENSIONS SECTION */}
          <Box className={classes.section} data-section="dimensions">
            <Group justify="space-between" mb="sm">
              <Group gap="xs">
                <ThemeIcon size="sm" variant="light" color="violet">
                  <IconLayoutGrid size={14} />
                </ThemeIcon>
                <Text size="sm" fw={600}>Group By (Dimensions)</Text>
                <Badge size="xs" variant="light">{state.dimensions.length}</Badge>
              </Group>
              <Button
                variant="light"
                size="xs"
                color="violet"
                leftSection={<IconPlus size={12} />}
                onClick={handleAddDimension}
              >
                Add
              </Button>
            </Group>
            
            {state.dimensions.length === 0 ? (
              <Box className={classes.emptyState}>
                <IconInfoCircle size={20} color="gray" />
                <Text size="xs" c="dimmed" ta="center" mt="xs">
                  No grouping. Results will be aggregated into a single row.
                </Text>
              </Box>
            ) : (
              <Stack gap="xs">
                {state.dimensions.map((dimension) => (
                  <Box key={dimension.id} className={classes.itemRow}>
                    <Group gap="xs" wrap="nowrap">
                      <Select
                        placeholder="Select column"
                        data={columnOptions}
                        value={dimension.column}
                        onChange={(v) => handleDimensionChange(dimension.id, { column: v || "" })}
                        size="xs"
                        style={{ flex: 1 }}
                        searchable
                      />
                      {enhancedColumns.find((c) => c.name === dimension.column)?.isJson && (
                        <TextInput
                          placeholder="$.field.path"
                          value={dimension.jsonPath || ""}
                          onChange={(e) => handleDimensionChange(dimension.id, { jsonPath: e.target.value })}
                          size="xs"
                          w={120}
                          leftSection={<IconBraces size={12} />}
                        />
                      )}
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        size="sm"
                        onClick={() => handleRemoveDimension(dimension.id)}
                      >
                        <IconTrash size={14} />
                      </ActionIcon>
                    </Group>
                  </Box>
                ))}
              </Stack>
            )}
          </Box>

          {/* FILTERS SECTION */}
          <Box className={classes.section} data-section="filters">
            <Group justify="space-between" mb="sm">
              <Group gap="xs">
                <ThemeIcon size="sm" variant="light" color="orange">
                  <IconFilter size={14} />
                </ThemeIcon>
                <Text size="sm" fw={600}>Filters (WHERE)</Text>
                <Badge size="xs" variant="light">{state.filters.length}</Badge>
              </Group>
              <Button
                variant="light"
                size="xs"
                color="orange"
                leftSection={<IconPlus size={12} />}
                onClick={handleAddFilter}
              >
                Add
              </Button>
            </Group>
            
            {state.filters.length === 0 ? (
              <Box className={classes.emptyState}>
                <IconInfoCircle size={20} color="gray" />
                <Text size="xs" c="dimmed" ta="center" mt="xs">
                  No filters. All data within the time range will be included.
                </Text>
              </Box>
            ) : (
              <Stack gap="xs">
                {state.filters.map((filter, index) => {
                  const operatorConfig = FILTER_OPERATORS.find((o) => o.value === filter.operator);
                  const needsValue = operatorConfig?.requiresValue !== false;
                  
                  return (
                    <Box key={filter.id} className={classes.itemRow}>
                      <Group gap={4} mb={4}>
                        <Text size="xs" c="dimmed">{index === 0 ? "WHERE" : "AND"}</Text>
                      </Group>
                      <Group gap="xs" wrap="nowrap">
                        <Select
                          placeholder="Column"
                          data={columnOptions}
                          value={filter.column}
                          onChange={(v) => handleFilterChange(filter.id, { column: v || "" })}
                          size="xs"
                          w={140}
                          searchable
                        />
                        {enhancedColumns.find((c) => c.name === filter.column)?.isJson && (
                          <TextInput
                            placeholder="$.path"
                            value={filter.jsonPath || ""}
                            onChange={(e) => handleFilterChange(filter.id, { jsonPath: e.target.value })}
                            size="xs"
                            w={80}
                            leftSection={<IconBraces size={10} />}
                          />
                        )}
                        <Select
                          placeholder="Operator"
                          data={FILTER_OPERATORS.map((o) => ({ value: o.value, label: o.label }))}
                          value={filter.operator}
                          onChange={(v) => handleFilterChange(filter.id, { operator: (v || "equals") as FilterOperator })}
                          size="xs"
                          w={120}
                        />
                        {needsValue && (
                          <TextInput
                            placeholder="Value"
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
                            style={{ flex: 1 }}
                          />
                        )}
                        <ActionIcon
                          variant="subtle"
                          color="red"
                          size="sm"
                          onClick={() => handleRemoveFilter(filter.id)}
                        >
                          <IconTrash size={14} />
                        </ActionIcon>
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
              <Text size="sm" fw={500}>Limit Results</Text>
              <NumberInput
                value={state.limit}
                onChange={handleLimitChange}
                min={1}
                max={10000}
                step={100}
                size="xs"
                w={100}
              />
              <Text size="xs" c="dimmed">rows (max 10,000)</Text>
            </Group>
          </Box>
        </Stack>
      </ScrollArea>

      <Divider />

      {/* SQL Preview */}
      <Box className={classes.sqlPreview}>
        <Group
          justify="space-between"
          className={classes.sqlPreviewHeader}
          onClick={toggleSqlPreview}
          style={{ cursor: "pointer" }}
        >
          <Group gap="xs">
            <IconCode size={14} />
            <Text size="xs" fw={600}>Generated SQL</Text>
          </Group>
          <ActionIcon variant="subtle" size="xs">
            {sqlPreviewOpen ? <IconChevronDown size={14} /> : <IconChevronUp size={14} />}
          </ActionIcon>
        </Group>
        <Collapse in={sqlPreviewOpen}>
          <ScrollArea h={120} className={classes.sqlPreviewCode}>
            <Code block className={classes.sqlCode}>
              {generatedSql || "-- Add metrics or dimensions to generate a query"}
            </Code>
          </ScrollArea>
        </Collapse>
      </Box>
    </Paper>
  );
}
