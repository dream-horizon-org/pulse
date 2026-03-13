import {
  Paper,
  Stack,
  Group,
  Select,
  TextInput,
  NumberInput,
  Switch,
  ActionIcon,
  Badge,
  Text,
  Tooltip,
  Box,
} from '@mantine/core';
import { IconTrash } from '@tabler/icons-react';
import {
  FilterCondition,
  FilterCategory,
  FilterOperator,
  FilterFieldDefinition,
  OPERATOR_LABELS,
} from '../../../services/sessionReplay/filterConfig';

interface ConditionRowProps {
  condition: FilterCondition;
  index: number;
  onUpdate: (updates: Partial<FilterCondition>) => void;
  onRemove: () => void;
  onCategoryChange: (category: FilterCategory) => void;
  onFieldChange: (field: string) => void;
  getFieldsByCategory: (category: FilterCategory) => FilterFieldDefinition[];
  getFieldDefinition: (fieldKey: string) => FilterFieldDefinition | null;
  categoryOptions: Array<{ value: string; label: string }>;
}

export function ConditionRow({
  condition,
  index,
  onUpdate,
  onRemove,
  onCategoryChange,
  onFieldChange,
  getFieldsByCategory,
  getFieldDefinition,
  categoryOptions,
}: ConditionRowProps) {
  const categoryFields = getFieldsByCategory(condition.category);
  const fieldDef = getFieldDefinition(condition.field);

  const renderValueInput = (
    fieldDef: FilterFieldDefinition,
    condition: FilterCondition,
    onUpdate: (updates: Partial<FilterCondition>) => void
  ) => {
    if (condition.operator === 'exists' || condition.operator === 'not_exists') {
      return (
        <Text size="sm" c="dimmed" style={{ paddingTop: 8 }}>
          No value needed for this operator
        </Text>
      );
    }

    switch (fieldDef.type) {
      case 'string':
        return (
          <TextInput
            label="Value"
            placeholder="Enter value"
            value={condition.value ?? ''}
            onChange={(e) => onUpdate({ value: e.target.value })}
          />
        );

      case 'number':
        return (
          <NumberInput
            label="Value"
            placeholder="Enter number"
            value={condition.value as number}
            onChange={(value) => onUpdate({ value: value ?? 0 })}
          />
        );

      case 'boolean':
        return (
          <Group gap="xs" style={{ paddingTop: 8 }}>
            <Text size="sm">Value:</Text>
            <Switch
              checked={condition.value === true}
              onChange={(e) => onUpdate({ value: e.currentTarget.checked })}
              size="md"
              onLabel="Yes"
              offLabel="No"
            />
          </Group>
        );

      case 'enum':
        return (
          <Select
            label="Value"
            placeholder="Select value"
            value={condition.value?.toString()}
            onChange={(value) => onUpdate({ value })}
            data={
              fieldDef.enumValues?.map((v) => ({
                value: v.toString(),
                label: v.toString(),
              })) ?? []
            }
          />
        );

      default:
        return (
          <TextInput
            label="Value"
            placeholder="Enter value"
            value={condition.value?.toString() ?? ''}
            onChange={(e) => onUpdate({ value: e.target.value })}
          />
        );
    }
  };

  return (
    <Paper p="md" withBorder radius="md" style={{ backgroundColor: 'white' }}>
      <Stack gap="sm">
        <Group justify="space-between" wrap="nowrap">
          <Badge color="teal" variant="light" size="lg" radius="sm">
            Condition {index + 1}
          </Badge>
          <Tooltip label="Remove condition">
            <ActionIcon color="red" variant="subtle" onClick={onRemove}>
              <IconTrash size={16} />
            </ActionIcon>
          </Tooltip>
        </Group>

        <Stack gap="xs">
          <Group grow wrap="wrap" gap="xs">
            <Select
              label="Category"
              placeholder="Select category"
              value={condition.category}
              onChange={(value) => value && onCategoryChange(value as FilterCategory)}
              data={categoryOptions}
            />
            <Select
              label="Field"
              placeholder="Select field"
              value={condition.field}
              onChange={(value) => value && onFieldChange(value)}
              data={categoryFields.map((field) => ({
                value: field.key,
                label: field.label,
              }))}
              disabled={!condition.category}
            />
          </Group>

          <Group grow wrap="wrap" gap="xs">
            <Select
              label="Operator"
              placeholder="Select operator"
              value={condition.operator}
              onChange={(value) => value && onUpdate({ operator: value as FilterOperator })}
              data={
                fieldDef?.operators.map((op) => ({
                  value: op,
                  label: OPERATOR_LABELS[op],
                })) ?? []
              }
              disabled={!fieldDef}
            />
            <Box style={{ minWidth: 180, flex: 1 }}>
              {fieldDef && renderValueInput(fieldDef, condition, onUpdate)}
            </Box>
          </Group>

          {fieldDef?.description && (
            <Text size="xs" c="dimmed" style={{ fontStyle: 'italic' }}>
              {fieldDef.description}
            </Text>
          )}
        </Stack>
      </Stack>
    </Paper>
  );
}
