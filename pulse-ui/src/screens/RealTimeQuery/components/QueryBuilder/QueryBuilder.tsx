/**
 * QueryBuilder Component - Grafana-style Simplified Version
 * Clean, intuitive query builder with toggleable sections
 */

import {
  Box,
  Stack,
  Paper,
  Group,
  Text,
  Button,
  NumberInput,
  Code,
  Collapse,
  ActionIcon,
  Tooltip,
  Skeleton,
  Select,
  TextInput,
  Switch,
  SegmentedControl,
  CopyButton,
  Badge,
} from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import {
  IconPlus,
  IconTrash,
  IconCode,
  IconCheck,
  IconCopy,
  IconSortAscending,
  IconSortDescending,
  IconX,
  IconClock,
  IconBraces,
  IconInfoCircle,
} from "@tabler/icons-react";
import { useState, useCallback, useEffect, useMemo } from "react";

import {
  QueryBuilderState,
  DEFAULT_QUERY_BUILDER_STATE,
  QueryBuilderProps,
  QueryColumn,
  FilterCondition,
  DataOperation,
  FilterOperator,
  SortDirection,
  TimeRangePreset,
  DATA_OPERATIONS,
  FILTER_OPERATORS,
  TIME_RANGE_PRESETS,
  enhanceColumns,
} from "./QueryBuilder.interface";
import { generateQuery } from "./utils/queryGenerator";

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
  const [state, setState] = useState<QueryBuilderState>(() => ({
    ...DEFAULT_QUERY_BUILDER_STATE,
    tableName,
    databaseName,
  }));

  // Update state when props change
  useEffect(() => {
    setState(prev => ({
      ...prev,
      tableName,
      databaseName,
    }));
  }, [tableName, databaseName]);

  // SQL preview visibility
  const [showPreview, setShowPreview] = useState(true);

  // Column options for dropdowns (with JSON indicator)
  const columnOptions = useMemo(() => {
    return enhancedColumns.map(col => ({
      value: col.name,
      label: col.isJson ? `${col.name} (JSON)` : col.name,
    }));
  }, [enhancedColumns]);

  // Helper to check if a column is JSON
  const isColumnJson = useCallback((columnName: string) => {
    const col = enhancedColumns.find(c => c.name === columnName);
    return col?.isJson || false;
  }, [enhancedColumns]);

  // Generate SQL query whenever state changes
  const generatedSql = useMemo(() => {
    if (!tableName || !databaseName) return "";
    return generateQuery(state, tableName, databaseName);
  }, [state, tableName, databaseName]);

  // Notify parent whenever generated SQL changes
  useEffect(() => {
    onQueryChange(generatedSql);
  }, [generatedSql, onQueryChange]);

  // === TIME RANGE HANDLERS ===
  const handleTimeRangeChange = useCallback((preset: string | null) => {
    if (!preset) return;
    setState(prev => ({
      ...prev,
      timeRange: { 
        ...prev.timeRange, 
        preset: preset as TimeRangePreset,
        ...(preset !== "custom" ? { startDate: undefined, endDate: undefined } : {}),
      },
    }));
  }, []);

  const handleStartDateChange = useCallback((date: Date | null) => {
    setState(prev => ({
      ...prev,
      timeRange: { ...prev.timeRange, startDate: date || undefined },
    }));
  }, []);

  const handleEndDateChange = useCallback((date: Date | null) => {
    setState(prev => ({
      ...prev,
      timeRange: { ...prev.timeRange, endDate: date || undefined },
    }));
  }, []);

  // === QUERY COLUMNS HANDLERS ===
  const handleAddColumn = useCallback(() => {
    const newColumn: QueryColumn = {
      id: generateId(),
      column: "",
      dataOperation: undefined,
      alias: "",
    };
    setState(prev => ({ ...prev, columns: [...prev.columns, newColumn] }));
  }, []);

  const handleRemoveColumn = useCallback((id: string) => {
    setState(prev => ({
      ...prev,
      columns: prev.columns.filter(c => c.id !== id),
    }));
  }, []);

  const handleColumnChange = useCallback((id: string, updates: Partial<QueryColumn>) => {
    setState(prev => ({
      ...prev,
      columns: prev.columns.map(c => (c.id === id ? { ...c, ...updates } : c)),
    }));
  }, []);

  // === FILTER HANDLERS ===
  const handleAddFilter = useCallback(() => {
    const newFilter: FilterCondition = {
      id: generateId(),
      column: "",
      operator: "=",
      value: "",
    };
    setState(prev => ({ ...prev, filters: [...prev.filters, newFilter] }));
  }, []);

  const handleRemoveFilter = useCallback((id: string) => {
    setState(prev => ({
      ...prev,
      filters: prev.filters.filter(f => f.id !== id),
    }));
  }, []);

  const handleFilterChange = useCallback((id: string, updates: Partial<FilterCondition>) => {
    setState(prev => ({
      ...prev,
      filters: prev.filters.map(f => (f.id === id ? { ...f, ...updates } : f)),
    }));
  }, []);

  // === GROUP BY HANDLERS ===
  const handleAddGroupBy = useCallback(() => {
    setState(prev => ({ ...prev, groupByColumns: [...prev.groupByColumns, ""] }));
  }, []);

  const handleRemoveGroupBy = useCallback((index: number) => {
    setState(prev => ({
      ...prev,
      groupByColumns: prev.groupByColumns.filter((_, i) => i !== index),
    }));
  }, []);

  const handleGroupByChange = useCallback((index: number, value: string) => {
    setState(prev => ({
      ...prev,
      groupByColumns: prev.groupByColumns.map((col, i) => (i === index ? value : col)),
    }));
  }, []);

  // === ORDER BY HANDLERS ===
  const handleOrderByColumnChange = useCallback((column: string | null) => {
    setState(prev => ({ ...prev, orderByColumn: column || "" }));
  }, []);

  const handleOrderByDirectionChange = useCallback((direction: SortDirection) => {
    setState(prev => ({ ...prev, orderDirection: direction }));
  }, []);

  const handleLimitChange = useCallback((value: string | number) => {
    // Allow clearing the limit (empty string or undefined)
    if (value === "" || value === undefined) {
      setState(prev => ({ ...prev, limit: undefined }));
      return;
    }
    const limit = typeof value === "string" ? parseInt(value, 10) : value;
    if (!isNaN(limit) && limit > 0 && limit <= 10000) {
      setState(prev => ({ ...prev, limit }));
    }
  }, []);

  // === SECTION TOGGLE HANDLERS ===
  const toggleFilter = useCallback(() => {
    setState(prev => ({ ...prev, showFilter: !prev.showFilter }));
  }, []);

  const toggleGroupBy = useCallback(() => {
    setState(prev => ({ ...prev, showGroupBy: !prev.showGroupBy }));
  }, []);

  const toggleOrderBy = useCallback(() => {
    setState(prev => ({ ...prev, showOrderBy: !prev.showOrderBy }));
  }, []);

  // Check if operator needs a value
  const operatorNeedsValue = (op: FilterOperator) => {
    return !["IS NULL", "IS NOT NULL"].includes(op);
  };

  // Loading skeleton
  if (isLoading) {
    return (
      <Paper className={classes.container} withBorder>
        <Stack gap="md" p="md">
          <Skeleton height={40} radius="md" />
          <Skeleton height={60} radius="md" />
          <Skeleton height={60} radius="md" />
          <Skeleton height={40} radius="md" />
        </Stack>
      </Paper>
    );
  }

    return (
      <Paper className={classes.container} withBorder>
      {/* Toggle Buttons Row */}
      <Box className={classes.toggleRow}>
        <Group gap="lg">
          <Switch
            label="Filter"
            checked={state.showFilter}
            onChange={toggleFilter}
            size="sm"
            color="teal"
          />
          <Switch
            label="Group"
            checked={state.showGroupBy}
            onChange={toggleGroupBy}
            size="sm"
            color="teal"
          />
          <Switch
            label="Order"
            checked={state.showOrderBy}
            onChange={toggleOrderBy}
            size="sm"
            color="teal"
          />
          <Switch
            label="Preview"
            checked={showPreview}
            onChange={() => setShowPreview(!showPreview)}
            size="sm"
            color="teal"
          />
          </Group>
        </Box>

      <Stack gap={0}>
        {/* Dataset and Table Row */}
        <Box className={classes.section}>
          <Group gap="md">
            <Box style={{ flex: 1 }}>
              <Text size="xs" fw={500} mb={4} c="dimmed">Dataset</Text>
              <TextInput
                value={databaseName}
                readOnly
                size="sm"
                variant="filled"
                classNames={{ input: classes.readOnlyInput }}
              />
      </Box>
            <Box style={{ flex: 1 }}>
              <Text size="xs" fw={500} mb={4} c="dimmed">Table</Text>
              <TextInput
                value={tableName}
                readOnly
                size="sm"
                variant="filled"
                classNames={{ input: classes.readOnlyInput }}
              />
            </Box>
          </Group>
      </Box>

        {/* Time Range Section - Always Required */}
        <Box className={classes.section}>
            <Group gap="xs" mb="xs">
            <IconClock size={14} color="var(--mantine-color-teal-6)" />
              <Text size="xs" fw={600}>Time Range</Text>
            <Badge size="xs" color="red" variant="light">Required</Badge>
            </Group>
          
          <Group gap="md" align="flex-end">
            <Box style={{ flex: 1, maxWidth: 200 }}>
            <Select
                data={TIME_RANGE_PRESETS}
                value={state.timeRange.preset}
              onChange={handleTimeRangeChange}
              size="sm"
              />
            </Box>
            
            {state.timeRange.preset === "custom" && (
              <>
                <Box>
                  <Text size="xs" c="dimmed" mb={4}>Start</Text>
                  <DateTimePicker
                    value={state.timeRange.startDate || null}
                    onChange={handleStartDateChange}
                    size="sm"
                    maxDate={state.timeRange.endDate || new Date()}
                    clearable
                    valueFormat="MMM D, YYYY HH:mm"
                    w={180}
                  />
                </Box>
                <Box>
                  <Text size="xs" c="dimmed" mb={4}>End</Text>
                  <DateTimePicker
                    value={state.timeRange.endDate || null}
                    onChange={handleEndDateChange}
                    size="sm"
                    minDate={state.timeRange.startDate || undefined}
                    maxDate={new Date()}
                    clearable
                    valueFormat="MMM D, YYYY HH:mm"
                    w={180}
                  />
                </Box>
              </>
            )}
                </Group>
          </Box>

        {/* Query Columns Section */}
        <Box className={classes.section}>
                <Group justify="space-between" mb="xs">
                  <Group gap="xs">
                    <Text size="xs" fw={600}>Columns</Text>
              <Text size="xs" c="dimmed">- optional</Text>
              {state.columns.length > 0 && (
                <Badge size="xs" variant="light" color="teal">{state.columns.length}</Badge>
              )}
                  </Group>
                </Group>
                
                  <Stack gap="xs">
            {state.columns.map((col) => (
              <Group key={col.id} gap="xs" wrap="nowrap" className={classes.itemRow}>
                <Tooltip label="Optional: Apply aggregation (COUNT, SUM, AVG, etc.)" position="top">
                          <Select
                    placeholder="— None —"
                    data={DATA_OPERATIONS}
                    value={col.dataOperation || null}
                    onChange={(v) => handleColumnChange(col.id, { dataOperation: (v as DataOperation) || undefined })}
                    size="sm"
                    clearable
                    w={120}
                    styles={{
                      input: {
                        color: col.dataOperation ? undefined : 'var(--mantine-color-gray-5)',
                        fontStyle: col.dataOperation ? undefined : 'italic',
                      }
                    }}
                  />
                </Tooltip>
                <Select
                  placeholder="Column *"
                  data={columnOptions}
                            value={col.column || null}
                  onChange={(v) => handleColumnChange(col.id, { column: v || "", jsonPath: "" })}
                  size="sm"
                            searchable
                  style={{ flex: 1 }}
                />
                {isColumnJson(col.column) && (
                  <Tooltip
                    label={
                      <div>
                        <div><strong>JSON Path Syntax:</strong></div>
                        <div>• Simple key: <code>name</code></div>
                        <div>• Nested: <code>address.city</code></div>
                        <div>• Key with dots: <code>["service.name"]</code></div>
                      </div>
                    }
                    multiline
                    w={260}
                    position="top"
                  >
                            <TextInput
                      placeholder="key or [&quot;key.name&quot;]"
                              value={col.jsonPath || ""}
                      onChange={(e) => handleColumnChange(col.id, { jsonPath: e.target.value })}
                      size="sm"
                      w={150}
                      leftSection={<IconBraces size={14} />}
                      rightSection={<IconInfoCircle size={14} style={{ opacity: 0.5 }} />}
                    />
                  </Tooltip>
                          )}
                          <TextInput
                            placeholder="Alias"
                            value={col.alias || ""}
                  onChange={(e) => handleColumnChange(col.id, { alias: e.target.value })}
                  size="sm"
                  w={100}
                />
                            <ActionIcon
                              variant="subtle"
                              color="red"
                              size="sm"
                  onClick={() => handleRemoveColumn(col.id)}
                            >
                  <IconTrash size={16} />
                            </ActionIcon>
                        </Group>
            ))}

              <Button
              variant="subtle"
              color="gray"
                size="xs"
              leftSection={<IconPlus size={14} />}
              onClick={handleAddColumn}
              className={classes.addButton}
            >
              Add column
              </Button>
          </Stack>

          {state.columns.length === 0 && (
            <Text size="xs" c="dimmed" fs="italic" mt="xs">
              No columns selected — will use SELECT *
                </Text>
            )}
                      </Box>

        {/* Filter Section */}
        <Collapse in={state.showFilter}>
          <Box className={classes.section}>
            <Group justify="space-between" mb="xs">
              <Group gap="xs">
                <Text size="xs" fw={600}>Filter by column value</Text>
                <Text size="xs" c="dimmed">- optional</Text>
              </Group>
            </Group>
            
              <Stack gap="xs">
              {state.filters.map((filter) => (
                <Group key={filter.id} gap="xs" wrap="nowrap" className={classes.itemRow}>
                      <Select
                              placeholder="Column"
                    data={columnOptions}
                    value={filter.column || null}
                    onChange={(v) => handleFilterChange(filter.id, { column: v || "", jsonPath: "" })}
                    size="sm"
                        searchable
                    style={{ flex: 1 }}
                      />
                  {isColumnJson(filter.column) && (
                            <Tooltip 
                      label={
                        <div>
                          <div><strong>JSON Path Syntax:</strong></div>
                          <div>• Simple key: <code>name</code></div>
                          <div>• Key with dots: <code>["service.name"]</code></div>
                        </div>
                      }
                      multiline
                      w={240}
                              position="top"
                    >
                      <TextInput
                        placeholder='["key.name"]'
                        value={filter.jsonPath || ""}
                        onChange={(e) => handleFilterChange(filter.id, { jsonPath: e.target.value })}
                        size="sm"
                        w={140}
                        leftSection={<IconBraces size={14} />}
                        rightSection={<IconInfoCircle size={14} style={{ opacity: 0.5 }} />}
                              />
                            </Tooltip>
                  )}
                      <Select
                    placeholder="Operator"
                    data={FILTER_OPERATORS}
                    value={filter.operator}
                    onChange={(v) => handleFilterChange(filter.id, { operator: (v as FilterOperator) || "=" })}
                    size="sm"
                    w={120}
                  />
                  {operatorNeedsValue(filter.operator) && (
                        <TextInput
                      placeholder="Value"
                      value={filter.value}
                      onChange={(e) => handleFilterChange(filter.id, { value: e.target.value })}
                      size="sm"
                      style={{ flex: 1 }}
                    />
                  )}
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        size="sm"
                    onClick={() => handleRemoveFilter(filter.id)}
                      >
                    <IconTrash size={16} />
                      </ActionIcon>
                    </Group>
              ))}

              <Button
                variant="subtle"
                color="gray"
                size="xs"
                leftSection={<IconPlus size={14} />}
                onClick={handleAddFilter}
                className={classes.addButton}
              >
                Add filter
              </Button>
            </Stack>
                    </Box>
        </Collapse>

        {/* Group By Section */}
        <Collapse in={state.showGroupBy}>
          <Box className={classes.section}>
            <Group justify="space-between" mb="sm">
              <Text size="xs" fw={600}>Group by column</Text>
                      </Group>

            <Box className={classes.groupByContainer}>
              {state.groupByColumns.map((col, index) => (
                  <Group key={index} gap="xs" wrap="nowrap" className={classes.itemRow}>
                      <Select
                    placeholder="Choose"
                    data={columnOptions}
                    value={col || null}
                    onChange={(v) => handleGroupByChange(index, v || "")}
                        size="xs"
                        searchable
                    w={300}
                    variant="unstyled"
                    styles={{ 
                      input: { 
                        padding: '2px 12px',
                        minHeight: 'auto',
                        height: 'auto',
                      } 
                    }}
                  />
                      <ActionIcon
                        variant="subtle"
                    color="gray"
                        size="sm"
                    onClick={() => handleRemoveGroupBy(index)}
                      >
                    <IconX size={14} />
                      </ActionIcon>
                    </Group>
              ))}

                        <ActionIcon
                variant="light"
                color="gray"
                size="lg"
                onClick={handleAddGroupBy}
                            radius="md"
                        >
                <IconPlus size={16} />
                        </ActionIcon>
                    </Box>
          </Box>
        </Collapse>

        {/* Order By + Limit Section */}
        <Collapse in={state.showOrderBy}>
          <Box className={classes.section}>
            <Group justify="space-between" mb="sm">
              <Text size="xs" fw={600}>Order by</Text>
            </Group>

            <Group gap="md">
              <Box style={{ flex: 1, maxWidth: 200 }}>
              <Select
                  placeholder="Select column"
                  data={columnOptions}
                  value={state.orderByColumn || null}
                  onChange={handleOrderByColumnChange}
                  size="sm"
                  w={200}
                searchable
                  clearable
              />
              </Box>
                <SegmentedControl
                value={state.orderDirection}
                onChange={(v) => handleOrderByDirectionChange(v as SortDirection)}
                size="sm"
                styles={{
                  innerLabel: {
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }
                }}
                  data={[
                  { value: "ASC", label: <IconSortAscending size={16} /> },
                  { value: "DESC", label: <IconSortDescending size={16} /> },
                ]}
              />
            <Group gap="xs" align="center">
              <Text size="xs" fw={600}>Limit</Text>
              <Text size="xs" c="dimmed">-</Text>
              <NumberInput
                value={state.limit ?? ""}
                onChange={handleLimitChange}
                placeholder="No limit"
                min={1}
                max={10000}
                size="xs"
                w={90}
                allowDecimal={false}
              />
            </Group>
          </Group>
      </Box>
        </Collapse>

        {/* SQL Preview Section */}
        <Collapse in={showPreview}>
          <Box className={classes.previewSection}>
            <Group justify="space-between" mb="xs">
          <Group gap="xs">
            <IconCode size={14} />
                <Text size="xs" fw={600}>Query Preview</Text>
          </Group>
            {generatedSql && (
              <CopyButton value={generatedSql}>
                {({ copied, copy }) => (
                    <Tooltip label={copied ? "Copied!" : "Copy SQL"}>
                      <ActionIcon variant="subtle" size="xs" color={copied ? "teal" : "gray"} onClick={copy}>
                      {copied ? <IconCheck size={12} /> : <IconCopy size={12} />}
                    </ActionIcon>
                  </Tooltip>
                )}
              </CopyButton>
            )}
          </Group>
            <Code block className={classes.sqlCode}>
              {generatedSql || "-- Configure columns to generate query"}
            </Code>
          </Box>
        </Collapse>
      </Stack>
    </Paper>
  );
}
