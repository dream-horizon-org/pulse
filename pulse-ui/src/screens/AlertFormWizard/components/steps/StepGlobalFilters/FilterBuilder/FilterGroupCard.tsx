import React, { useCallback } from "react";
import { Box, Button, SegmentedControl, ActionIcon, Text, Stack } from "@mantine/core";
import { IconPlus, IconTrash } from "@tabler/icons-react";
import { FilterGroup, FilterCondition, LogicalOperator, createEmptyCondition } from "./FilterBuilder.interface";
import { FilterConditionRow } from "./FilterConditionRow";
import classes from "./FilterBuilder.module.css";

interface FilterGroupCardProps {
  group: FilterGroup;
  availableFields: { value: string; label: string; options: string[] }[];
  onUpdate: (updates: Partial<FilterGroup>) => void;
  onRemove: () => void;
  canRemove: boolean;
  groupIndex: number;
}

export const FilterGroupCard: React.FC<FilterGroupCardProps> = ({
  group, availableFields, onUpdate, onRemove, canRemove, groupIndex,
}) => {
  const updateCondition = useCallback((idx: number, updates: Partial<FilterCondition>) => {
    const updated = group.conditions.map((c, i) => i === idx ? { ...c, ...updates } : c);
    onUpdate({ conditions: updated });
  }, [group.conditions, onUpdate]);

  const removeCondition = useCallback((idx: number) => {
    if (group.conditions.length <= 1) return;
    onUpdate({ conditions: group.conditions.filter((_, i) => i !== idx) });
  }, [group.conditions, onUpdate]);

  const addCondition = useCallback(() => {
    onUpdate({ conditions: [...group.conditions, createEmptyCondition()] });
  }, [group.conditions, onUpdate]);

  return (
    <Box className={classes.groupCard}>
      <Box className={classes.groupHeader}>
        <Text size="sm" fw={500}>Group {groupIndex + 1}</Text>
        <Box className={classes.groupControls}>
          <SegmentedControl
            size="xs"
            value={group.logicalOperator}
            onChange={(v) => onUpdate({ logicalOperator: v as LogicalOperator })}
            data={[{ value: "AND", label: "AND" }, { value: "OR", label: "OR" }]}
          />
          {canRemove && (
            <ActionIcon color="red" variant="subtle" onClick={onRemove} size="sm">
              <IconTrash size={14} />
            </ActionIcon>
          )}
        </Box>
      </Box>

      <Stack gap="xs">
        {group.conditions.map((condition, idx) => (
          <Box key={condition.id}>
            {idx > 0 && (
              <Text size="xs" c="dimmed" ta="center" mb={4}>{group.logicalOperator}</Text>
            )}
            <FilterConditionRow
              condition={condition}
              availableFields={availableFields}
              onUpdate={(updates) => updateCondition(idx, updates)}
              onRemove={() => removeCondition(idx)}
              canRemove={group.conditions.length > 1}
            />
          </Box>
        ))}
      </Stack>

      <Button variant="subtle" size="xs" leftSection={<IconPlus size={12} />} onClick={addCondition} mt="xs">
        Add Condition
      </Button>
    </Box>
  );
};

