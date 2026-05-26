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
} from "@mantine/core";
import { IconTrash, IconAlertCircle } from "@tabler/icons-react";
import {
  FilterCondition,
  FilterCategory,
  FilterOperator,
  FilterFieldDefinition,
  OPERATOR_LABELS,
} from "../../../services/sessionReplay/filterConfig";
import { validateCondition } from "./AdvancedFilterBuilder/filterValidation";

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
  operatorLabels?: Record<string, string>;
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
  operatorLabels,
}: ConditionRowProps) {
  const categoryFields = getFieldsByCategory(condition.category);
  const fieldDef = getFieldDefinition(condition.field);
  const validationError = validateCondition(condition);
  const hasError = !!validationError;

  const isValueFieldDisabled =
    condition.operator === "exists" ||
    condition.operator === "not_exists" ||
    String(condition.operator).toUpperCase() === "EMPTY" ||
    String(condition.operator).toUpperCase() === "NOT_EMPTY";

  const renderValueInput = (
    fieldDef: FilterFieldDefinition,
    condition: FilterCondition,
    onUpdate: (updates: Partial<FilterCondition>) => void,
  ) => {
    switch (fieldDef.type) {
      case "string":
        return (
          <TextInput
            label="Value"
            placeholder="Enter value"
            value={condition.value ?? ""}
            onChange={(e) => onUpdate({ value: e.target.value })}
            error={hasError ? validationError : false}
            disabled={isValueFieldDisabled}
          />
        );

      case "number":
        return (
          <NumberInput
            label="Value"
            placeholder="Enter number"
            value={condition.value as number}
            onChange={(value) => onUpdate({ value: value ?? 0 })}
            error={hasError ? validationError : false}
            disabled={isValueFieldDisabled}
          />
        );

      case "boolean":
        return (
          <Group gap="xs" style={{ paddingTop: 0 }}>
            <Text size="sm">Value:</Text>
            <Switch
              checked={condition.value === true}
              onChange={(e) => onUpdate({ value: e.currentTarget.checked })}
              size="md"
              onLabel="Yes"
              offLabel="No"
              disabled={isValueFieldDisabled}
            />
          </Group>
        );

      case "enum":
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
            error={hasError ? validationError : false}
            disabled={isValueFieldDisabled}
          />
        );

      default:
        return (
          <TextInput
            label="Value"
            placeholder="Enter value"
            value={condition.value?.toString() ?? ""}
            onChange={(e) => onUpdate({ value: e.target.value })}
            error={hasError ? validationError : false}
            disabled={isValueFieldDisabled}
          />
        );
    }
  };

  return (
    <Paper p="md" withBorder radius="md" style={{ backgroundColor: "white" }}>
      <Stack gap="sm">
        <Group justify="space-between" wrap="nowrap">
          <Group gap="xs" wrap="nowrap">
            <Badge color="teal" variant="light" size="lg" radius="sm">
              Condition {index + 1}
            </Badge>
            {hasError && (
              <Tooltip label={validationError}>
                <IconAlertCircle size={18} color="#fa5252" />
              </Tooltip>
            )}
          </Group>
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
              onChange={(value) =>
                value && onCategoryChange(value as FilterCategory)
              }
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

          <Group grow wrap="wrap" gap="xs" align="flex-start">
            <Select
              label="Operator"
              placeholder="Select operator"
              value={condition.operator}
              onChange={(value) =>
                value && onUpdate({ operator: value as FilterOperator })
              }
              data={
                fieldDef?.operators.map((op) => ({
                  value: op,
                  label:
                    operatorLabels?.[op] ??
                    OPERATOR_LABELS[op as keyof typeof OPERATOR_LABELS] ??
                    op,
                })) ?? []
              }
              disabled={!fieldDef}
            />
            <Box
              style={{
                minWidth: 180,
                flex: 1,
              }}
            >
              {fieldDef && renderValueInput(fieldDef, condition, onUpdate)}
            </Box>
          </Group>

          {fieldDef?.description && (
            <Text size="xs" c="dimmed" style={{ fontStyle: "italic" }}>
              {fieldDef.description}
            </Text>
          )}
        </Stack>
      </Stack>
    </Paper>
  );
}
