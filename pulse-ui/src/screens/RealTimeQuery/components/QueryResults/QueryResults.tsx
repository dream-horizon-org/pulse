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
} from "@mantine/core";
import {
  IconDownload,
  IconRefresh,
  IconDatabaseOff,
} from "@tabler/icons-react";
import ReactECharts from "echarts-for-react";
import { useState, useMemo } from "react";
import { QueryResult, VisualizationConfig } from "../../RealTimeQuery.interface";
import classes from "./QueryResults.module.css";

interface QueryResultsProps {
  data: QueryResult | null;
  visualization: VisualizationConfig;
  isLoading: boolean;
  error: string | null;
  onRefresh?: () => void;
  onExport?: () => void;
}

const ROWS_PER_PAGE = 25;

export function QueryResults({
  data,
  visualization,
  isLoading,
  error,
  onRefresh,
  onExport,
}: QueryResultsProps) {
  const [currentPage, setCurrentPage] = useState(1);

  const chartOptions = useMemo(() => {
    if (!data || data.rows.length === 0) return null;

    const columns = data.columns;
    const rows = data.rows;

    // Assume first column is dimension, rest are metrics
    const dimensionColumn = columns[0]?.name;
    const metricColumns = columns.slice(1).map((c) => c.name);

    const xAxisData = rows.map((row) => String(row[dimensionColumn] || ""));

    const series = metricColumns.map((metricName, index) => {
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
    const start = (currentPage - 1) * ROWS_PER_PAGE;
    const end = start + ROWS_PER_PAGE;
    return data.rows.slice(start, end);
  }, [data, currentPage]);

  const totalPages = data ? Math.ceil(data.rows.length / ROWS_PER_PAGE) : 0;

  if (isLoading) {
    return (
      <Paper className={classes.container} p="xl" withBorder>
        <Center className={classes.loadingState}>
          <Stack align="center" gap="md">
            <Loader color="teal" size="lg" />
            <Text size="sm" c="dimmed">Running query...</Text>
          </Stack>
        </Center>
      </Paper>
    );
  }

  if (error) {
    return (
      <Paper className={classes.container} p="xl" withBorder>
        <Center className={classes.errorState}>
          <Stack align="center" gap="md">
            <IconDatabaseOff size={48} stroke={1.5} color="var(--mantine-color-red-5)" />
            <Text size="sm" c="red" ta="center">{error}</Text>
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

  if (!data || data.rows.length === 0) {
    return (
      <Paper className={classes.container} p="xl" withBorder>
        <Center className={classes.emptyState}>
          <Stack align="center" gap="md">
            <IconDatabaseOff size={48} stroke={1.5} color="var(--mantine-color-gray-4)" />
            <Text size="sm" c="dimmed" ta="center">
              No data to display. Run a query to see results.
            </Text>
          </Stack>
        </Center>
      </Paper>
    );
  }

  return (
    <Paper className={classes.container} p="md" withBorder>
      <Stack gap="md">
        {/* Header */}
        <Group justify="space-between">
          <Group gap="sm">
            <Badge variant="light" color="teal" size="sm">
              {data.totalRows.toLocaleString()} rows
            </Badge>
            <Badge variant="light" color="gray" size="sm">
              {data.executionTimeMs}ms
            </Badge>
          </Group>
          <Group gap="xs">
            {onExport && (
              <Button
                variant="subtle"
                size="xs"
                leftSection={<IconDownload size={14} />}
                onClick={onExport}
              >
                Export CSV
              </Button>
            )}
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
        {visualization.chartType === "table" ? (
          <Box className={classes.tableWrapper}>
            <ScrollArea>
              <Table striped highlightOnHover withTableBorder withColumnBorders className={classes.table}>
                <Table.Thead>
                  <Table.Tr>
                    {data.columns.map((col) => (
                      <Table.Th key={col.name}>{col.name}</Table.Th>
                    ))}
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {paginatedRows.map((row, rowIndex) => (
                    <Table.Tr key={rowIndex}>
                      {data.columns.map((col) => (
                        <Table.Td key={col.name}>
                          {row[col.name] !== null && row[col.name] !== undefined
                            ? String(row[col.name])
                            : "-"}
                        </Table.Td>
                      ))}
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </ScrollArea>
            {totalPages > 1 && (
              <Group justify="center" mt="md">
                <Pagination
                  total={totalPages}
                  value={currentPage}
                  onChange={setCurrentPage}
                  size="sm"
                  color="teal"
                />
              </Group>
            )}
          </Box>
        ) : (
          <Box className={classes.chartWrapper}>
            {chartOptions && (
              <ReactECharts
                option={chartOptions}
                style={{ height: "400px", width: "100%" }}
                opts={{ renderer: "canvas" }}
              />
            )}
          </Box>
        )}
      </Stack>
    </Paper>
  );
}

