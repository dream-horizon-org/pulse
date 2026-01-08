import {
  Box,
  Button,
  Group,
  Select,
  TextInput,
  ActionIcon,
  Stack,
  Paper,
  Text,
  Badge,
} from "@mantine/core";
import { IconPlus, IconX } from "@tabler/icons-react";
import { v4 as uuidV4 } from "uuid";
import { Metric, FieldMetadata, AggregationType } from "../../RealTimeQuery.interface";
import { AGGREGATION_TYPES } from "../../RealTimeQuery.constants";
import classes from "./MetricSelector.module.css";

interface MetricSelectorProps {
  metrics: Metric[];
  fields: FieldMetadata[];
  onChange: (metrics: Metric[]) => void;
}

export function MetricSelector({ metrics, fields, onChange }: MetricSelectorProps) {
  const numericFields = fields.filter((f) => f.type === "number");
  const allFields = fields;

  const getFieldOptions = (aggregation: AggregationType) => {
    const aggConfig = AGGREGATION_TYPES.find((a) => a.value === aggregation);
    if (!aggConfig?.requiresField) return [];
    
    // For count_distinct, allow all fields
    if (aggregation === "count_distinct") {
      return allFields.map((f) => ({ value: f.name, label: f.name }));
    }
    
    // For numeric aggregations, only show numeric fields
    return numericFields.map((f) => ({ value: f.name, label: f.name }));
  };

  const addMetric = () => {
    const newMetric: Metric = {
      id: uuidV4(),
      aggregation: "count",
      field: null,
      alias: `Metric ${metrics.length + 1}`,
    };
    onChange([...metrics, newMetric]);
  };

  const updateMetric = (id: string, updates: Partial<Metric>) => {
    onChange(
      metrics.map((m) => {
        if (m.id !== id) return m;
        const updated = { ...m, ...updates };
        // Reset field if aggregation doesn't require one
        const aggConfig = AGGREGATION_TYPES.find((a) => a.value === updated.aggregation);
        if (!aggConfig?.requiresField) {
          updated.field = null;
        }
        return updated;
      })
    );
  };

  const removeMetric = (id: string) => {
    if (metrics.length === 1) return; // Keep at least one metric
    onChange(metrics.filter((m) => m.id !== id));
  };

  return (
    <Box className={classes.container}>
      <Stack gap="sm">
        {metrics.map((metric, index) => {
          const aggConfig = AGGREGATION_TYPES.find((a) => a.value === metric.aggregation);
          const needsField = aggConfig?.requiresField;

          return (
            <Paper key={metric.id} className={classes.metricRow} p="xs" withBorder>
              <Group gap="xs" wrap="nowrap" align="flex-start">
                <Badge
                  size="sm"
                  variant="light"
                  color="teal"
                  className={classes.indexBadge}
                >
                  {index + 1}
                </Badge>
                <Stack gap="xs" style={{ flex: 1 }}>
                  <Group gap="xs" wrap="nowrap">
                    <Select
                      size="xs"
                      placeholder="Aggregation"
                      value={metric.aggregation}
                      onChange={(value) =>
                        updateMetric(metric.id, {
                          aggregation: value as AggregationType,
                        })
                      }
                      data={AGGREGATION_TYPES.map((a) => ({
                        value: a.value,
                        label: a.label,
                      }))}
                      className={classes.aggregationSelect}
                      comboboxProps={{ withinPortal: true }}
                    />
                    {needsField && (
                      <>
                        <Text size="xs" c="dimmed">of</Text>
                        <Select
                          size="xs"
                          placeholder="Field"
                          value={metric.field}
                          onChange={(value) => updateMetric(metric.id, { field: value })}
                          data={getFieldOptions(metric.aggregation)}
                          className={classes.fieldSelect}
                          searchable
                          comboboxProps={{ withinPortal: true }}
                        />
                      </>
                    )}
                    {!needsField && (
                      <Text size="xs" c="dimmed" className={classes.allRecords}>
                        (all records)
                      </Text>
                    )}
                  </Group>
                  <Group gap="xs" wrap="nowrap">
                    <Text size="xs" c="dimmed">as</Text>
                    <TextInput
                      size="xs"
                      placeholder="Alias (optional)"
                      value={metric.alias || ""}
                      onChange={(e) => updateMetric(metric.id, { alias: e.target.value })}
                      className={classes.aliasInput}
                    />
                  </Group>
                </Stack>
                <ActionIcon
                  size="sm"
                  variant="subtle"
                  color="red"
                  onClick={() => removeMetric(metric.id)}
                  disabled={metrics.length === 1}
                  className={classes.removeButton}
                >
                  <IconX size={14} />
                </ActionIcon>
              </Group>
            </Paper>
          );
        })}

        <Button
          variant="subtle"
          color="teal"
          size="xs"
          leftSection={<IconPlus size={14} />}
          onClick={addMetric}
          className={classes.addButton}
        >
          Add Metric
        </Button>
      </Stack>
    </Box>
  );
}

