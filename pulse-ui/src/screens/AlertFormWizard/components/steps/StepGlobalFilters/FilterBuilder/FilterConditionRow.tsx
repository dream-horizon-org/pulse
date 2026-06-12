import React from "react";
import { Group, Select, ActionIcon, Autocomplete } from "@mantine/core";
import { IconTrash } from "@tabler/icons-react";
import { FilterCondition, FilterOperator, FILTER_OPERATORS } from "./FilterBuilder.interface";

interface FilterConditionRowProps {
  condition: FilterCondition;
  availableFields: { value: string; label: string; options: string[] }[];
  onUpdate: (updates: Partial<FilterCondition>) => void;
  onRemove: () => void;
  canRemove: boolean;
}

export const FilterConditionRow: React.FC<FilterConditionRowProps> = ({
  condition, availableFields, onUpdate, onRemove, canRemove,
}) => {
  const fieldOptions = availableFields.map((f) => ({ value: f.value, label: f.label }));
  const selectedField = availableFields.find((f) => f.value === condition.field);
  const valueOptions = selectedField?.options || [];

  return (
    <Group gap="xs" wrap="nowrap" style={{ width: "100%" }}>
      <Select
        placeholder="Field"
        data={fieldOptions}
        value={condition.field || null}
        onChange={(v) => onUpdate({ field: v || "", value: "" })}
        size="sm"
        style={{ flex: 1, minWidth: 120 }}
        searchable
      />
      <Select
        placeholder="Op"
        data={FILTER_OPERATORS.map((o) => ({ value: o.value, label: o.label }))}
        value={condition.operator}
        onChange={(v) => onUpdate({ operator: (v as FilterOperator) || "=" })}
        size="sm"
        style={{ width: 100 }}
      />
      <Autocomplete
        placeholder="Value"
        data={valueOptions}
        value={condition.value}
        onChange={(v) => onUpdate({ value: v })}
        size="sm"
        style={{ flex: 1, minWidth: 120 }}
      />
      {canRemove && (
        <ActionIcon color="red" variant="subtle" onClick={onRemove} size="sm">
          <IconTrash size={14} />
        </ActionIcon>
      )}
    </Group>
  );
};

