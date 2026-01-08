import {
  Box,
  Paper,
  Text,
  Stack,
  Group,
  Badge,
  Button,
  Table,
  ScrollArea,
  Loader,
  Center,
  Pagination,
  RingProgress,
  ThemeIcon,
  Tooltip,
  CopyButton,
  ActionIcon,
} from "@mantine/core";
import {
  IconRefresh,
  IconDatabaseOff,
  IconAlertTriangle,
  IconChevronDown,
  IconDatabase,
  IconCopy,
  IconCheck,
} from "@tabler/icons-react";
import ReactECharts from "echarts-for-react";
import { useState, useMemo, useEffect } from "react";
import { QueryResult, VisualizationConfig } from "../../RealTimeQuery.interface";
import { formatBytes, formatDuration, RESULTS_PAGE_SIZE } from "../../RealTimeQuery.constants";
import classes from "./QueryResults.module.css";

interface QueryResultsProps {
  data: QueryResult | null;
  visualization: VisualizationConfig;
  isLoading: boolean;
  isLoadingMore?: boolean;
  error: string | null;
  onRefresh?: () => void;
  onLoadMore?: () => void;
}

export function QueryResults({
  data,
  visualization,
  isLoading,
  isLoadingMore = false,
  error,
  onRefresh,
  onLoadMore,
}: QueryResultsProps) {
  const [currentPage, setCurrentPage] = useState(1);

  // Reset page when data changes
  useEffect(() => {
    setCurrentPage(1);
  }, [data?.totalRows]);

  const chartOptions = useMemo(() => {
    if (!data || data.rows.length === 0 || visualization.chartType === "table") {
      return null;
    }

    const columns = data.columns;
    const rows = data.rows;

    // Assume first column is dimension, rest are metrics
    const dimensionColumn = columns[0]?.name;
    const metricColumns = columns.slice(1).filter(c => 
      // Only include numeric-looking columns for charts
      rows.some(row => typeof row[c.name] === "number" || !isNaN(Number(row[c.name])))
    ).map(c => c.name);

    if (metricColumns.length === 0) {
      return null;
    }

    const xAxisData = rows.map((row) => {
      const val = row[dimensionColumn];
      if (val === null || val === undefined) return "";
      // Truncate long labels
      const str = String(val);
      return str.length > 20 ? str.slice(0, 17) + "..." : str;
    });

    const series = metricColumns.map((metricName) => {
      const seriesData = rows.map((row) => {
        const value = row[metricName];
        return typeof value === "number" ? value : Number(value) || 0;
      });

      const baseConfig = {
        name: metricName,
        data: seriesData,
        smooth: true,
      };

      switch (visualization.chartType) {
        case "line":
          return {
            ...baseConfig,
            type: "line",
            lineStyle: { width: 2 },
            areaStyle: undefined,
          };
        case "area":
          return {
            ...baseConfig,
            type: "line",
            areaStyle: { opacity: 0.3 },
            stack: visualization.stacked ? "total" : undefined,
          };
        case "bar":
          return {
            ...baseConfig,
            type: "bar",
            stack: visualization.stacked ? "total" : undefined,
            barMaxWidth: 40,
          };
        case "pie":
          return {
            type: "pie",
            name: metricName,
            radius: ["40%", "70%"],
            center: ["50%", "50%"],
            data: rows.map((row) => ({
              name: String(row[dimensionColumn] || ""),
              value: typeof row[metricName] === "number" ? row[metricName] : Number(row[metricName]) || 0,
            })),
            label: {
              show: visualization.showLegend,
            },
          };
        default:
          return baseConfig;
      }
    });

    const isPie = visualization.chartType === "pie";

    return {
      tooltip: {
        trigger: isPie ? "item" : "axis",
        backgroundColor: "rgba(255, 255, 255, 0.95)",
        borderColor: "#e2e8f0",
        borderWidth: 1,
        textStyle: {
          color: "#1e293b",
        },
      },
      legend: visualization.showLegend && !isPie
        ? {
            type: "scroll",
            bottom: 0,
            data: metricColumns,
          }
        : undefined,
      grid: isPie
        ? undefined
        : {
            left: "3%",
            right: "4%",
            bottom: visualization.showLegend ? 50 : 30,
            top: 30,
            containLabel: true,
          },
      xAxis: isPie
        ? undefined
        : {
            type: "category",
            data: xAxisData,
            axisLabel: {
              rotate: xAxisData.length > 10 ? 45 : 0,
              interval: 0,
            },
          },
      yAxis: isPie
        ? undefined
        : {
            type: "value",
          },
      series,
      color: [
        "#0ba09a",
        "#6366f1",
        "#f59e0b",
        "#ef4444",
        "#8b5cf6",
        "#10b981",
        "#3b82f6",
        "#f97316",
      ],
    };
  }, [data, visualization]);

  const paginatedRows = useMemo(() => {
    if (!data) return [];
    const start = (currentPage - 1) * RESULTS_PAGE_SIZE;
    const end = start + RESULTS_PAGE_SIZE;
    return data.rows.slice(start, end);
  }, [data, currentPage]);

  const totalPages = data ? Math.ceil(data.rows.length / RESULTS_PAGE_SIZE) : 0;

  // Loading state - Enhanced visual indicator
  if (isLoading) {
    return (
      <Paper className={classes.container} p="xl" withBorder>
        <Center className={classes.loadingState}>
          <Stack align="center" gap="lg">
            <Box className={classes.loadingIndicator}>
              <RingProgress
                size={100}
                thickness={4}
                roundCaps
                sections={[{ value: 100, color: "teal" }]}
                label={
                  <Center>
                    <ThemeIcon color="teal" variant="light" radius="xl" size={60}>
                      <IconDatabase size={28} />
                    </ThemeIcon>
                  </Center>
                }
              />
              <Box className={classes.loadingPulse} />
            </Box>
            <Stack align="center" gap={4}>
              <Text size="md" fw={600} c="dark.6">Executing Query</Text>
              <Text size="sm" c="dimmed">Fetching results from the database...</Text>
            </Stack>
            <Group gap="xs">
              <Loader size="xs" color="teal" />
              <Text size="xs" c="dimmed">This may take a few seconds</Text>
            </Group>
          </Stack>
        </Center>
      </Paper>
    );
  }

  // Error state
  if (error) {
    return (
      <Paper className={classes.container} p="xl" withBorder>
        <Center className={classes.errorState}>
          <Stack align="center" gap="md">
            <IconAlertTriangle size={48} stroke={1.5} color="var(--mantine-color-red-5)" />
            <Stack align="center" gap="xs">
              <Text size="sm" fw={500} c="red">Query Failed</Text>
              <Text size="xs" c="dimmed" ta="center" maw={400}>
                {error}
              </Text>
            </Stack>
            {onRefresh && (
              <Button
                variant="light"
                color="teal"
                size="sm"
                leftSection={<IconRefresh size={16} />}
                onClick={onRefresh}
              >
                Try Again
              </Button>
            )}
          </Stack>
        </Center>
      </Paper>
    );
  }

  // Empty state - no query run yet
  if (!data) {
    return (
      <Paper className={classes.container} p="xl" withBorder>
        <Center className={classes.emptyState}>
          <Stack align="center" gap="md">
            <IconDatabaseOff size={48} stroke={1.5} color="var(--mantine-color-gray-4)" />
            <Stack align="center" gap="xs">
              <Text size="sm" fw={500} c="dimmed">No Results Yet</Text>
              <Text size="xs" c="dimmed" ta="center">
                Write a SQL query and click "Run Query" to see results here.
              </Text>
            </Stack>
          </Stack>
        </Center>
      </Paper>
    );
  }

  // Empty results
  if (data.rows.length === 0) {
    return (
      <Paper className={classes.container} p="xl" withBorder>
        <Center className={classes.emptyState}>
          <Stack align="center" gap="md">
            <IconDatabaseOff size={48} stroke={1.5} color="var(--mantine-color-orange-4)" />
            <Stack align="center" gap="xs">
              <Text size="sm" fw={500} c="dimmed">No Data Found</Text>
              <Text size="xs" c="dimmed" ta="center">
                Your query returned no results. Try adjusting your filters or time range.
              </Text>
            </Stack>
            {onRefresh && (
              <Button
                variant="light"
                color="teal"
                size="sm"
                leftSection={<IconRefresh size={16} />}
                onClick={onRefresh}
              >
                Run Again
              </Button>
            )}
          </Stack>
        </Center>
      </Paper>
    );
  }

  return (
    <Paper className={classes.container} withBorder>
      <Stack gap={0}>
        {/* Header */}
        <Group justify="space-between" p="md">
          <Group gap="sm">
            <Badge variant="light" color="teal" size="sm">
              {data.totalRows.toLocaleString()} rows
            </Badge>
            {data.executionTimeMs !== undefined && (
              <Badge variant="light" color="gray" size="sm">
                {formatDuration(data.executionTimeMs)}
              </Badge>
            )}
            {data.dataScannedInBytes !== undefined && (
              <Badge variant="light" color="gray" size="sm">
                {formatBytes(data.dataScannedInBytes)} scanned
              </Badge>
            )}
            {data.hasMore && (
              <Badge variant="light" color="orange" size="sm">
                More data available
              </Badge>
            )}
          </Group>
          <Group gap="xs">
            {onRefresh && (
              <Button
                variant="subtle"
                size="xs"
                leftSection={<IconRefresh size={14} />}
                onClick={onRefresh}
              >
                Refresh
              </Button>
            )}
          </Group>
        </Group>

        {/* Chart or Table */}
        {visualization.chartType !== "table" && chartOptions ? (
          <Box className={classes.chartWrapper} p="md">
            <ReactECharts
              option={chartOptions}
              style={{ height: "400px", width: "100%" }}
              opts={{ renderer: "canvas" }}
            />
          </Box>
        ) : (
          <Box className={classes.tableWrapper}>
            <ScrollArea h={500} className={classes.scrollArea}>
              <Table striped highlightOnHover withTableBorder withColumnBorders className={classes.table}>
                <Table.Thead>
                  <Table.Tr>
                    {data.columns.map((col) => (
                      <Table.Th key={col.name}>
                        <Group gap={4} wrap="nowrap">
                          <Text size="xs" fw={600}>{col.name}</Text>
                          <Text size="xs" c="dimmed">({col.type})</Text>
                        </Group>
                      </Table.Th>
                    ))}
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {paginatedRows.map((row, rowIndex) => (
                    <Table.Tr key={rowIndex}>
                      {data.columns.map((col) => {
                        const cellValue = row[col.name];
                        const isNull = cellValue === null || cellValue === undefined;
                        const stringValue = isNull ? "" : String(cellValue);
                        const isLongContent = stringValue.length > 50;
                        
                        return (
                          <Table.Td key={col.name} className={classes.tableCell}>
                            {isNull ? (
                              <Text size="xs" c="dimmed" fs="italic">&lt;null&gt;</Text>
                            ) : isLongContent ? (
                              <Group gap={4} wrap="nowrap" className={classes.cellContent}>
                                <Tooltip
                                  label={
                                    <Box className={classes.tooltipContent}>
                                      <ScrollArea.Autosize mah={300} maw={400}>
                                        <Text size="xs" style={{ whiteSpace: "pre-wrap", wordBreak: "break-all" }}>
                                          {stringValue}
                                        </Text>
                                      </ScrollArea.Autosize>
                                    </Box>
                                  }
                                  multiline
                                  w="auto"
                                  maw={420}
                                  position="top"
                                  withArrow
                                  arrowSize={8}
                                >
                                  <Text size="xs" className={classes.truncatedText}>
                                    {stringValue}
                                  </Text>
                                </Tooltip>
                                <CopyButton value={stringValue}>
                                  {({ copied, copy }) => (
                                    <ActionIcon 
                                      size="xs" 
                                      variant="subtle" 
                                      color={copied ? "teal" : "gray"}
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        copy();
                                      }}
                                      className={classes.copyButton}
                                    >
                                      {copied ? <IconCheck size={12} /> : <IconCopy size={12} />}
                                    </ActionIcon>
                                  )}
                                </CopyButton>
                              </Group>
                            ) : (
                              <Text size="xs">{stringValue}</Text>
                            )}
                          </Table.Td>
                        );
                      })}
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </ScrollArea>
          </Box>
        )}

        {/* Pagination Footer */}
        <Box className={classes.paginationFooter}>
          <Group justify="space-between" align="center">
            <Text size="xs" c="dimmed">
              Showing {((currentPage - 1) * RESULTS_PAGE_SIZE) + 1} - {Math.min(currentPage * RESULTS_PAGE_SIZE, data.totalRows)} of {data.totalRows} loaded rows
            </Text>
            
            <Group gap="md">
              {/* Client-side pagination */}
              {totalPages > 1 && (
                <Pagination
                  total={totalPages}
                  value={currentPage}
                  onChange={setCurrentPage}
                  size="sm"
                  color="teal"
                />
              )}
              
              {/* Load More button for API pagination */}
              {data.hasMore && onLoadMore && (
                <Button
                  variant="light"
                  color="teal"
                  size="xs"
                  leftSection={isLoadingMore ? <Loader size={14} color="teal" /> : <IconChevronDown size={14} />}
                  onClick={onLoadMore}
                  disabled={isLoadingMore}
                  className={classes.loadMoreButton}
                >
                  {isLoadingMore ? "Loading..." : "Load More"}
                </Button>
              )}
            </Group>
          </Group>
        </Box>
      </Stack>
    </Paper>
  );
}
