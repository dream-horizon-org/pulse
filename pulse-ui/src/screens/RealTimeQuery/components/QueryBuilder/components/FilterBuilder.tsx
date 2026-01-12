/**
 * FilterBuilder Component
 * Build WHERE conditions for the query
 */

import {
  Box,
  Group,
  Text,
  Select,
  TextInput,
  ActionIcon,
  Button,
  Stack,
  Tooltip,
} from "@mantine/core";
import { IconPlus, IconTrash, IconFilter } from "@tabler/icons-react";
import { Filter, FilterOperator, FILTER_OPERATORS } from "../QueryBuilder.interface";
import classes from "./FilterBuilder.module.css";

interface FilterBuilderProps {
  filters: Filter[];
  availableColumns: { name: string; type: string }[];
  onChange: (filters: Filter[]) => void;
}

// Generate unique ID
const generateId = () => `filter_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

export function FilterBuilder({ filters, availableColumns, onChange }: FilterBuilderProps) {
  const handleAddFilter = () => {
    const newFilter: Filter = {
      id: generateId(),
      column: "",
      operator: "equals",
      value: "",
    };
    onChange([...filters, newFilter]);
  };

  const handleRemoveFilter = (id: string) => {
    onChange(filters.filter((f) => f.id !== id));
  };

  const handleFilterChange = (id: string, field: keyof Filter, value: string | string[] | FilterOperator) => {
    onChange(
      filters.map((f): Filter => {
        if (f.id !== id) return f;
        
        // Handle different field types
        if (field === "operator") {
          const newOperator = value as FilterOperator;
          // Reset value when changing to is_null or is_not_null
          if (["is_null", "is_not_null"].includes(newOperator)) {
            return { ...f, operator: newOperator, value: "" };
          }
          return { ...f, operator: newOperator };
        }
        
        if (field === "column") {
          return { ...f, column: value as string };
        }
        
        if (field === "value") {
          return { ...f, value: value as string | string[] };
        }
        
        return f;
      })
    );
  };

  const columnOptions = availableColumns.map((c) => ({
    value: c.name,
    label: `${c.name} (${c.type})`,
  }));

  const operatorOptions = FILTER_OPERATORS.map((op) => ({
    value: op.value,
    label: op.label,
  }));

  const getOperatorConfig = (operator: FilterOperator) => {
    return FILTER_OPERATORS.find((op) => op.value === operator);
  };

  return (
    <Box className={classes.container}>
      <Group justify="space-between" mb="sm">
        <Group gap="xs">
          <Box className={classes.iconWrapper}>
            <IconFilter size={14} />
          </Box>
          <Text size="sm" fw={600}>Filters</Text>
          <Text size="xs" c="dimmed">({filters.length})</Text>
        </Group>
        <Tooltip label="Add filter">
          <ActionIcon
            variant="light"
            color="orange"
            size="sm"
            onClick={handleAddFilter}
          >
            <IconPlus size={14} />
          </ActionIcon>
        </Tooltip>
      </Group>

      {filters.length === 0 ? (
        <Box className={classes.emptyState}>
          <Text size="xs" c="dimmed" ta="center">
            No filters. All data in the time range will be included.
          </Text>
          <Button
            variant="subtle"
            size="xs"
            color="orange"
            leftSection={<IconPlus size={12} />}
            onClick={handleAddFilter}
            mt="xs"
          >
            Add Filter
          </Button>
        </Box>
      ) : (
        <Stack gap="xs">
          {filters.map((filter, index) => {
            const operatorConfig = getOperatorConfig(filter.operator);
            const needsValue = operatorConfig?.requiresValue ?? true;
            
            return (
              <Box key={filter.id} className={classes.filterRow}>
                <Group gap="xs" mb={4}>
                  <Text size="xs" c="dimmed" w={20} ta="center">
                    {index + 1}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {index === 0 ? "WHERE" : "AND"}
                  </Text>
                </Group>
                <Group gap="xs" wrap="nowrap" pl={28}>
                  <Select
                    placeholder="Column"
                    data={columnOptions}
                    value={filter.column}
                    onChange={(v) => handleFilterChange(filter.id, "column", v || "")}
                    size="xs"
                    style={{ flex: 1 }}
                    searchable
                    classNames={{ input: classes.select }}
                  />
                  <Select
                    placeholder="Operator"
                    data={operatorOptions}
                    value={filter.operator}
                    onChange={(v) => handleFilterChange(filter.id, "operator", v || "equals")}
                    size="xs"
                    w={120}
                    classNames={{ input: classes.select }}
                  />
                  {needsValue && (
                    <TextInput
                      placeholder="Value"
                      value={Array.isArray(filter.value) ? filter.value.join(", ") : filter.value}
                      onChange={(e) => {
                        const val = e.target.value;
                        // For "in" and "not_in", split by comma
                        if (operatorConfig?.isArrayValue) {
                          handleFilterChange(filter.id, "value", val.split(",").map((v) => v.trim()));
                        } else {
                          handleFilterChange(filter.id, "value", val);
                        }
                      }}
                      size="xs"
                      style={{ flex: 1 }}
                      classNames={{ input: classes.input }}
                    />
                  )}
                  <Tooltip label="Remove">
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      size="sm"
                      onClick={() => handleRemoveFilter(filter.id)}
                    >
                      <IconTrash size={14} />
                    </ActionIcon>
                  </Tooltip>
                </Group>
              </Box>
            );
          })}
        </Stack>
      )}
    </Box>
  );
}

