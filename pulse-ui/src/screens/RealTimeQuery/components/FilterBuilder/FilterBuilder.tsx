import {
  Box,
  Button,
  Group,
  Select,
  TextInput,
  ActionIcon,
  Badge,
  Text,
  Stack,
  Paper,
  SegmentedControl,
} from "@mantine/core";
import { IconPlus, IconX, IconFilter } from "@tabler/icons-react";
import { v4 as uuidV4 } from "uuid";
import { FilterGroup, FilterCondition, FieldMetadata, FilterLogic } from "../../RealTimeQuery.interface";
import { FILTER_OPERATORS } from "../../RealTimeQuery.constants";
import classes from "./FilterBuilder.module.css";

interface FilterBuilderProps {
  filters: FilterGroup;
  fields: FieldMetadata[];
  onChange: (filters: FilterGroup) => void;
}

export function FilterBuilder({ filters, fields, onChange }: FilterBuilderProps) {
  const fieldOptions = fields.map((f) => ({
    value: f.name,
    label: f.name,
  }));

  const getOperatorsForField = (fieldName: string) => {
    const field = fields.find((f) => f.name === fieldName);
    if (!field) return FILTER_OPERATORS;
    return FILTER_OPERATORS.filter((op) => op.types.includes(field.type));
  };

  const addCondition = () => {
    const newCondition: FilterCondition = {
      id: uuidV4(),
      field: fields[0]?.name || "",
      operator: "equals",
      value: "",
    };
    onChange({
      ...filters,
      conditions: [...filters.conditions, newCondition],
    });
  };

  const updateCondition = (id: string, updates: Partial<FilterCondition>) => {
    onChange({
      ...filters,
      conditions: filters.conditions.map((c) =>
        c.id === id ? { ...c, ...updates } : c
      ),
    });
  };

  const removeCondition = (id: string) => {
    onChange({
      ...filters,
      conditions: filters.conditions.filter((c) => c.id !== id),
    });
  };

  const updateLogic = (logic: FilterLogic) => {
    onChange({
      ...filters,
      logic,
    });
  };

  return (
    <Box className={classes.container}>
      {filters.conditions.length === 0 ? (
        <Paper className={classes.emptyState} p="md" withBorder>
          <Stack align="center" gap="xs">
            <IconFilter size={24} stroke={1.5} color="var(--mantine-color-gray-5)" />
            <Text size="sm" c="dimmed" ta="center">
              No filters applied. Add filters to narrow down your data.
            </Text>
            <Button
              variant="light"
              color="teal"
              size="xs"
              leftSection={<IconPlus size={14} />}
              onClick={addCondition}
            >
              Add Filter
            </Button>
          </Stack>
        </Paper>
      ) : (
        <Stack gap="sm">
          {filters.conditions.length > 1 && (
            <Group gap="xs">
              <Text size="xs" c="dimmed">Match</Text>
              <SegmentedControl
                size="xs"
                value={filters.logic}
                onChange={(value) => updateLogic(value as FilterLogic)}
                data={[
                  { label: "All (AND)", value: "AND" },
                  { label: "Any (OR)", value: "OR" },
                ]}
                className={classes.logicSwitch}
              />
              <Text size="xs" c="dimmed">of the following</Text>
            </Group>
          )}

          {filters.conditions.map((condition, index) => (
            <Paper key={condition.id} className={classes.filterRow} p="xs" withBorder>
              <Group gap="xs" wrap="nowrap">
                {index > 0 && (
                  <Badge size="xs" variant="light" color="gray" className={classes.logicBadge}>
                    {filters.logic}
                  </Badge>
                )}
                <Select
                  size="xs"
                  placeholder="Field"
                  value={condition.field}
                  onChange={(value) => updateCondition(condition.id, { field: value || "" })}
                  data={fieldOptions}
                  className={classes.fieldSelect}
                  searchable
                  comboboxProps={{ withinPortal: true }}
                />
                <Select
                  size="xs"
                  placeholder="Operator"
                  value={condition.operator}
                  onChange={(value) =>
                    updateCondition(condition.id, {
                      operator: value as FilterCondition["operator"],
                    })
                  }
                  data={getOperatorsForField(condition.field).map((op) => ({
                    value: op.value,
                    label: op.label,
                  }))}
                  className={classes.operatorSelect}
                  comboboxProps={{ withinPortal: true }}
                />
                {condition.operator !== "is_null" && condition.operator !== "is_not_null" && (
                  <TextInput
                    size="xs"
                    placeholder="Value"
                    value={String(condition.value || "")}
                    onChange={(e) => updateCondition(condition.id, { value: e.target.value })}
                    className={classes.valueInput}
                  />
                )}
                <ActionIcon
                  size="sm"
                  variant="subtle"
                  color="red"
                  onClick={() => removeCondition(condition.id)}
                  className={classes.removeButton}
                >
                  <IconX size={14} />
                </ActionIcon>
              </Group>
            </Paper>
          ))}

          <Button
            variant="subtle"
            color="teal"
            size="xs"
            leftSection={<IconPlus size={14} />}
            onClick={addCondition}
            className={classes.addButton}
          >
            Add Filter
          </Button>
        </Stack>
      )}
    </Box>
  );
}

