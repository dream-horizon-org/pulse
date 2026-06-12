import React, { useCallback } from "react";
import { Box, Button, SegmentedControl, Text, Stack, Loader, Paper } from "@mantine/core";
import { IconPlus } from "@tabler/icons-react";
import { FilterBuilderProps, FilterGroup, LogicalOperator, createEmptyGroup } from "./FilterBuilder.interface";
import { FilterGroupCard } from "./FilterGroupCard";
import classes from "./FilterBuilder.module.css";

export const FilterBuilder: React.FC<FilterBuilderProps> = ({
  value, onChange, availableFields, isLoading = false,
}) => {
  const updateGroup = useCallback((idx: number, updates: Partial<FilterGroup>) => {
    const updated = value.groups.map((g, i) => i === idx ? { ...g, ...updates } : g);
    onChange({ ...value, groups: updated });
  }, [value, onChange]);

  const removeGroup = useCallback((idx: number) => {
    if (value.groups.length <= 1) return;
    onChange({ ...value, groups: value.groups.filter((_, i) => i !== idx) });
  }, [value, onChange]);

  const addGroup = useCallback(() => {
    onChange({ ...value, groups: [...value.groups, createEmptyGroup()] });
  }, [value, onChange]);

  if (isLoading) {
    return (
      <Box className={classes.loadingContainer}>
        <Loader size="sm" />
        <Text size="sm" c="dimmed">Loading filter options...</Text>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      {value.groups.length > 1 && (
        <Box className={classes.groupOperatorContainer}>
          <Text size="sm" fw={500}>Combine groups with:</Text>
          <SegmentedControl
            size="xs"
            value={value.groupOperator}
            onChange={(v) => onChange({ ...value, groupOperator: v as LogicalOperator })}
            data={[{ value: "AND", label: "AND" }, { value: "OR", label: "OR" }]}
          />
        </Box>
      )}

      <Stack gap="md">
        {value.groups.map((group, idx) => (
          <Box key={group.id}>
            {idx > 0 && (
              <Text size="sm" c="teal" ta="center" fw={600} my="xs">{value.groupOperator}</Text>
            )}
            <FilterGroupCard
              group={group}
              groupIndex={idx}
              availableFields={availableFields}
              onUpdate={(updates) => updateGroup(idx, updates)}
              onRemove={() => removeGroup(idx)}
              canRemove={value.groups.length > 1}
            />
          </Box>
        ))}
      </Stack>

      <Button variant="light" leftSection={<IconPlus size={14} />} onClick={addGroup} mt="md" color="teal">
        Add Filter Group
      </Button>

      {/* Preview of generated expression */}
      <Paper className={classes.preview} mt="md" p="sm" withBorder>
        <Text size="xs" fw={500} mb={4}>Generated Expression:</Text>
        <Text size="xs" c="dimmed" style={{ fontFamily: "monospace", wordBreak: "break-all" }}>
          {generateExpression(value) || "(No filters configured)"}
        </Text>
      </Paper>
    </Box>
  );
};

function generateExpression(data: FilterBuilderProps["value"]): string {
  const groupExpressions = data.groups
    .map((group) => {
      const conditions = group.conditions
        .filter((c) => c.field && c.value)
        .map((c) => {
          const val = c.operator === "LIKE" ? `'%${c.value}%'` : `'${c.value}'`;
          const op = c.operator === "LIKE" ? "LIKE" : c.operator;
          return `\`${c.field}\` ${op} ${val}`;
        });
      if (conditions.length === 0) return "";
      if (conditions.length === 1) return conditions[0];
      return `(${conditions.join(` ${group.logicalOperator} `)})`;
    })
    .filter(Boolean);

  if (groupExpressions.length === 0) return "";
  if (groupExpressions.length === 1) return groupExpressions[0];
  return groupExpressions.join(` ${data.groupOperator} `);
}

