/**
 * MetricSelector Component
 * Select aggregation functions and columns for metrics
 */

import {
  Box,
  Group,
  Text,
  Select,
  ActionIcon,
  Button,
  Stack,
  Tooltip,
} from "@mantine/core";
import { IconPlus, IconTrash, IconChartBar } from "@tabler/icons-react";
import { Metric, AggregationFunction, AGGREGATION_OPTIONS } from "../QueryBuilder.interface";
import classes from "./MetricSelector.module.css";

interface MetricSelectorProps {
  metrics: Metric[];
  availableColumns: { name: string; type: string }[];
  onChange: (metrics: Metric[]) => void;
}

// Generate unique ID
const generateId = () => `metric_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

// Get numeric columns for SUM, AVG, etc.
const getNumericTypes = () => ["bigint", "integer", "double", "float", "decimal", "numeric", "int", "long"];

export function MetricSelector({ metrics, availableColumns, onChange }: MetricSelectorProps) {
  const handleAddMetric = () => {
    const newMetric: Metric = {
      id: generateId(),
      column: "*",
      aggregation: "COUNT",
    };
    onChange([...metrics, newMetric]);
  };

  const handleRemoveMetric = (id: string) => {
    onChange(metrics.filter((m) => m.id !== id));
  };

  const handleMetricChange = (id: string, field: keyof Metric, value: string) => {
    onChange(
      metrics.map((m): Metric => {
        if (m.id !== id) return m;
        
        // Handle different field types with proper typing
        if (field === "aggregation") {
          const newAggregation = value as AggregationFunction;
          const needsColumn = ["SUM", "AVG", "MIN", "MAX"].includes(newAggregation);
          if (needsColumn && m.column === "*") {
            // Find first numeric column
            const numericCol = availableColumns.find((c) => 
              getNumericTypes().some((t) => c.type.toLowerCase().includes(t))
            );
            return { ...m, aggregation: newAggregation, column: numericCol?.name || m.column };
          }
          return { ...m, aggregation: newAggregation };
        }
        
        if (field === "column") {
          return { ...m, column: value };
        }
        
        if (field === "alias") {
          return { ...m, alias: value };
        }
        
        return m;
      })
    );
  };

  // Get available columns for aggregation type
  const getColumnsForAggregation = (aggregation: AggregationFunction) => {
    if (aggregation === "COUNT") {
      return [{ value: "*", label: "All rows (*)" }, ...availableColumns.map((c) => ({ value: c.name, label: `${c.name} (${c.type})` }))];
    }
    
    if (["SUM", "AVG", "MIN", "MAX"].includes(aggregation)) {
      // Only numeric columns
      const numericCols = availableColumns.filter((c) =>
        getNumericTypes().some((t) => c.type.toLowerCase().includes(t))
      );
      return numericCols.map((c) => ({ value: c.name, label: `${c.name} (${c.type})` }));
    }
    
    // COUNT_DISTINCT - all columns
    return availableColumns.map((c) => ({ value: c.name, label: `${c.name} (${c.type})` }));
  };

  const aggregationOptions = AGGREGATION_OPTIONS.map((opt) => ({
    value: opt.value,
    label: opt.label,
  }));

  return (
    <Box className={classes.container}>
      <Group justify="space-between" mb="sm">
        <Group gap="xs">
          <Box className={classes.iconWrapper}>
            <IconChartBar size={14} />
          </Box>
          <Text size="sm" fw={600}>Metrics</Text>
          <Text size="xs" c="dimmed">({metrics.length})</Text>
        </Group>
        <Tooltip label="Add metric">
          <ActionIcon
            variant="light"
            color="teal"
            size="sm"
            onClick={handleAddMetric}
          >
            <IconPlus size={14} />
          </ActionIcon>
        </Tooltip>
      </Group>

      {metrics.length === 0 ? (
        <Box className={classes.emptyState}>
          <Text size="xs" c="dimmed" ta="center">
            No metrics added. Click + to add aggregations.
          </Text>
          <Button
            variant="subtle"
            size="xs"
            leftSection={<IconPlus size={12} />}
            onClick={handleAddMetric}
            mt="xs"
          >
            Add Metric
          </Button>
        </Box>
      ) : (
        <Stack gap="xs">
          {metrics.map((metric, index) => (
            <Box key={metric.id} className={classes.metricRow}>
              <Group gap="xs" wrap="nowrap">
                <Text size="xs" c="dimmed" w={20} ta="center">
                  {index + 1}
                </Text>
                <Select
                  placeholder="Function"
                  data={aggregationOptions}
                  value={metric.aggregation}
                  onChange={(v) => handleMetricChange(metric.id, "aggregation", v || "COUNT")}
                  size="xs"
                  w={100}
                  classNames={{ input: classes.select }}
                />
                <Select
                  placeholder="Column"
                  data={getColumnsForAggregation(metric.aggregation)}
                  value={metric.column}
                  onChange={(v) => handleMetricChange(metric.id, "column", v || "*")}
                  size="xs"
                  style={{ flex: 1 }}
                  searchable
                  classNames={{ input: classes.select }}
                />
                <Tooltip label="Remove">
                  <ActionIcon
                    variant="subtle"
                    color="red"
                    size="sm"
                    onClick={() => handleRemoveMetric(metric.id)}
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
  );
}

