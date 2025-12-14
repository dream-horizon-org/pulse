/**
 * Metric Condition Card Component
 * 
 * Shows metric/operator selection with thresholds for ALL global scope names.
 * Scope names are selected globally, not per-condition.
 */

import React, { useCallback } from "react";
import { Box, Text, Select, NumberInput, ActionIcon, Group, Badge, Divider } from "@mantine/core";
import { IconTrash } from "@tabler/icons-react";
import { MetricCondition, MetricOperator } from "../../../types";
import { METRIC_OPERATOR_OPTIONS } from "../../../constants";
import classes from "./StepMetricsAndExpression.module.css";

interface MetricConditionCardProps {
  condition: MetricCondition;
  metrics: string[];
  globalScopeNames: string[];
  isAppVitals: boolean;
  isMetricsLoading: boolean;
  onUpdate: (updates: Partial<MetricCondition>) => void;
  onRemove: () => void;
  canRemove: boolean;
}

export const MetricConditionCard: React.FC<MetricConditionCardProps> = ({
  condition, metrics, globalScopeNames, isAppVitals, isMetricsLoading, onUpdate, onRemove, canRemove,
}) => {
  const metricOptions = metrics.map((m) => ({ value: m, label: m.replace(/_/g, " ") }));

  const handleThresholdChange = useCallback((scopeName: string, value: number) => {
    onUpdate({ threshold: { ...condition.threshold, [scopeName]: value } });
  }, [condition.threshold, onUpdate]);

  return (
    <Box className={classes.conditionCard}>
      <Group justify="space-between" mb="md">
        <Badge size="lg" variant="filled" color="teal">Condition {condition.alias}</Badge>
        {canRemove && (
          <ActionIcon color="red" variant="subtle" onClick={onRemove}>
            <IconTrash size={16} />
          </ActionIcon>
        )}
      </Group>

      {/* Metric and Operator Selection */}
      <Group grow mb="md">
        <Select
          label="Metric"
          placeholder={isMetricsLoading ? "Loading..." : "Select metric"}
          data={metricOptions}
          value={condition.metric || null}
          onChange={(v) => {
            const newMetric = v || "";
            // For App Vitals, also initialize threshold with "value" key
            if (isAppVitals && newMetric) {
              onUpdate({ metric: newMetric, threshold: { value: condition.threshold["value"] ?? 0 } });
            } else {
              onUpdate({ metric: newMetric });
            }
          }}
          disabled={isMetricsLoading}
          searchable
        />
        <Select
          label="Operator"
          data={METRIC_OPERATOR_OPTIONS.map((o) => ({ value: o.value, label: o.label }))}
          value={condition.operator}
          onChange={(v) => onUpdate({ operator: v as MetricOperator })}
        />
      </Group>

      {/* Thresholds for each global scope name */}
      {!isAppVitals && globalScopeNames.length > 0 && (
        <>
          <Divider my="md" label="Thresholds" labelPosition="center" />
          <Box className={classes.thresholdsGrid}>
            {globalScopeNames.map((scopeName) => (
              <Group key={scopeName} gap="sm" className={classes.thresholdRow}>
                <Text size="sm" fw={500} className={classes.scopeNameLabel}>{scopeName}</Text>
                <NumberInput
                  size="sm"
                  placeholder="Threshold"
                  value={condition.threshold[scopeName] ?? 0}
                  onChange={(v) => handleThresholdChange(scopeName, Number(v) || 0)}
                  className={classes.thresholdInput}
                  decimalScale={2}
                />
              </Group>
            ))}
          </Box>
        </>
      )}

      {!isAppVitals && globalScopeNames.length === 0 && (
        <Text size="sm" c="dimmed" ta="center" mt="sm">
          Please select scope names above to configure thresholds.
        </Text>
      )}

      {/* For App Vitals - simple threshold input */}
      {isAppVitals && (
        <NumberInput
          label="Threshold"
          placeholder="Enter value"
          value={condition.threshold["value"] ?? 0}
          onChange={(v) => onUpdate({ threshold: { value: Number(v) || 0 } })}
          decimalScale={2}
        />
      )}
    </Box>
  );
};

