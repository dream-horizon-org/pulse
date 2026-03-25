import { ScrollArea, Stack } from "@mantine/core";
import type {
  FilterCondition,
  FilterCategory,
} from "../../../../services/sessionReplay/filterConfig";
import type { FilterFieldDefinition } from "../../../../services/sessionReplay/filterConfig";
import { ConditionRow } from "../ConditionRow";
import { FilterEmptyState } from "./FilterEmptyState";

export interface FilterConditionsListProps {
  conditions: FilterCondition[];
  onUpdate: (id: string, updates: Partial<FilterCondition>) => void;
  onRemove: (id: string) => void;
  onCategoryChange: (id: string, category: FilterCategory) => void;
  onFieldChange: (id: string, fieldKey: string) => void;
  getFieldsByCategory: (category: FilterCategory) => FilterFieldDefinition[];
  getFieldDefinition: (fieldKey: string) => FilterFieldDefinition | null;
  categoryOptions: Array<{ value: string; label: string }>;
  operatorLabels?: Record<string, string>;
}

export function FilterConditionsList({
  conditions,
  onUpdate,
  onRemove,
  onCategoryChange,
  onFieldChange,
  getFieldsByCategory,
  getFieldDefinition,
  categoryOptions,
  operatorLabels,
}: FilterConditionsListProps) {
  return (
    <ScrollArea style={{ flex: 1 }} offsetScrollbars>
      <Stack gap="sm" pr="xs">
        {conditions.length === 0 && <FilterEmptyState />}
        {conditions.map((condition, index) => (
          <ConditionRow
            key={condition.id}
            condition={condition}
            index={index}
            onUpdate={(updates) => onUpdate(condition.id, updates)}
            onRemove={() => onRemove(condition.id)}
            onCategoryChange={(category) =>
              onCategoryChange(condition.id, category)
            }
            onFieldChange={(field) => onFieldChange(condition.id, field)}
            getFieldsByCategory={getFieldsByCategory}
            getFieldDefinition={getFieldDefinition}
            categoryOptions={categoryOptions}
            operatorLabels={operatorLabels}
          />
        ))}
      </Stack>
    </ScrollArea>
  );
}
