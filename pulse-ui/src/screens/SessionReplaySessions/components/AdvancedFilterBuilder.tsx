import { useState, useMemo } from "react";
import {
  Modal,
  Stack,
  Group,
  Button,
  Select,
  ActionIcon,
  Paper,
  Text,
  Divider,
  Tooltip,
  ScrollArea,
  Box,
  Loader,
} from "@mantine/core";
import { IconPlus, IconFilter, IconInfoCircle } from "@tabler/icons-react";
import {
  FilterCondition,
  FilterGroup,
  FilterCategory,
  FilterFieldDefinition,
  FilterOperator,
} from "../../../services/sessionReplay/filterConfig";
import type { FilterConfigResponse } from "../../../services/sessionReplay/types";
import { v4 as uuidv4 } from "uuid";
import { ConditionRow } from "./ConditionRow";
import { useFilterSchema } from "../hooks/useFilterSchema";

function adaptSessionsFilterConfig(config: FilterConfigResponse): {
  categories: Array<{
    key: string;
    label: string;
    fields: FilterFieldDefinition[];
  }>;
} {
  return {
    categories: config.advanced.map((c) => ({
      key: c.categoryKey,
      label: c.displayName,
      fields: c.fields.map((f) => ({
        key: f.key,
        label: f.displayName,
        category: c.categoryKey as FilterCategory,
        type: (f.dataType === "integer" || f.dataType === "float"
          ? "number"
          : "string") as "string" | "number" | "boolean" | "date" | "enum",
        operators: f.allowedOperators.map((o) => o.key) as FilterOperator[],
      })),
    })),
  };
}

interface AdvancedFilterBuilderProps {
  opened: boolean;
  onClose: () => void;
  onApply: (filterGroup: FilterGroup) => void;
  initialFilters?: FilterGroup;
  sessionsFilterConfig?: FilterConfigResponse | null;
}

export function AdvancedFilterBuilder({
  opened,
  onClose,
  onApply,
  initialFilters,
  sessionsFilterConfig,
}: AdvancedFilterBuilderProps) {
  const { schema: legacySchema, loading: schemaLoading } = useFilterSchema({
    skip: !!sessionsFilterConfig,
  });

  const effectiveSchema = useMemo(() => {
    if (sessionsFilterConfig)
      return adaptSessionsFilterConfig(sessionsFilterConfig);
    return legacySchema
      ? {
          categories: legacySchema.categories.map((cat) => ({
            key: cat.key,
            label: cat.label,
            fields: cat.fields.map((f) => ({
              ...f,
              category: f.category as FilterCategory,
              operators: f.operators as FilterOperator[],
            })),
          })),
        }
      : null;
  }, [sessionsFilterConfig, legacySchema]);

  const [filterGroup, setFilterGroup] = useState<FilterGroup>(
    initialFilters ?? {
      id: uuidv4(),
      operator: "AND",
      conditions: [],
    },
  );

  const getFieldsByCategory = (
    category: FilterCategory,
  ): FilterFieldDefinition[] => {
    if (!effectiveSchema) return [];
    const categoryData = effectiveSchema.categories.find(
      (c) => c.key === category,
    );
    if (!categoryData) return [];
    return categoryData.fields;
  };

  const getFieldDefinition = (
    fieldKey: string,
  ): FilterFieldDefinition | null => {
    if (!effectiveSchema) return null;
    for (const category of effectiveSchema.categories) {
      const field = category.fields.find((f) => f.key === fieldKey);
      if (field) return field;
    }
    return null;
  };

  const categoryOptions = useMemo(() => {
    if (!effectiveSchema) return [];
    return effectiveSchema.categories.map((cat) => ({
      value: cat.key,
      label: cat.label,
    }));
  }, [effectiveSchema]);

  const operatorLabelsFromConfig = useMemo(() => {
    if (!sessionsFilterConfig) return undefined;
    const map: Record<string, string> = {};
    for (const c of sessionsFilterConfig.advanced) {
      for (const f of c.fields) {
        for (const o of f.allowedOperators) map[o.key] = o.label;
      }
    }
    return map;
  }, [sessionsFilterConfig]);

  const addCondition = () => {
    const firstCategory = effectiveSchema?.categories[0];
    const firstField = firstCategory?.fields[0];
    const newCondition: FilterCondition = {
      id: uuidv4(),
      category: (firstCategory?.key ?? "ui_interaction") as FilterCategory,
      field: firstField?.key ?? "interaction.type",
      operator: (firstField?.operators[0] ?? "equals") as FilterOperator,
      value: firstField?.type === "boolean" ? true : "",
    };

    setFilterGroup({
      ...filterGroup,
      conditions: [...filterGroup.conditions, newCondition],
    });
  };

  const updateCondition = (id: string, updates: Partial<FilterCondition>) => {
    setFilterGroup({
      ...filterGroup,
      conditions: filterGroup.conditions.map((condition: FilterCondition) =>
        condition.id === id ? { ...condition, ...updates } : condition,
      ),
    });
  };

  const removeCondition = (id: string) => {
    setFilterGroup({
      ...filterGroup,
      conditions: filterGroup.conditions.filter(
        (condition: FilterCondition) => condition.id !== id,
      ),
    });
  };

  const handleCategoryChange = (id: string, category: FilterCategory) => {
    const categoryFields = getFieldsByCategory(category);
    const firstField = categoryFields[0];

    if (firstField) {
      updateCondition(id, {
        category,
        field: firstField.key,
        operator: firstField.operators[0],
        value: firstField.type === "boolean" ? true : "",
      });
    }
  };

  const handleFieldChange = (id: string, fieldKey: string) => {
    const fieldDef = getFieldDefinition(fieldKey);

    if (fieldDef) {
      updateCondition(id, {
        field: fieldKey,
        operator: fieldDef.operators[0],
        value: fieldDef.type === "boolean" ? true : "",
      });
    }
  };

  const handleApply = () => {
    onApply(filterGroup);
    onClose();
  };

  const handleClear = () => {
    setFilterGroup({
      id: uuidv4(),
      operator: "AND",
      conditions: [],
    });
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={
        <Group gap="xs">
          <IconFilter size={20} />
          <Text fw={600}>Advanced Filters</Text>
          <Tooltip
            label="Build complex queries with multiple conditions"
            position="right"
          >
            <ActionIcon variant="transparent" size="sm" c="dimmed">
              <IconInfoCircle size={16} />
            </ActionIcon>
          </Tooltip>
        </Group>
      }
      size="xl"
      padding="lg"
      styles={{
        body: {
          maxHeight: "calc(100vh - 200px)",
          display: "flex",
          flexDirection: "column",
        },
      }}
    >
      <Stack gap="md" style={{ flex: 1, minHeight: 0 }}>
        {(sessionsFilterConfig ? false : schemaLoading) ? (
          <Stack align="center" justify="center" py="xl">
            <Loader size="lg" />
            <Text size="sm" c="dimmed">
              Loading filter options...
            </Text>
          </Stack>
        ) : !effectiveSchema ? (
          <Paper p="xl" withBorder style={{ backgroundColor: "#fff3cd" }}>
            <Text size="sm" c="red" ta="center">
              Failed to load filter schema. Please try again.
            </Text>
          </Paper>
        ) : (
          <>
            <Group justify="space-between" wrap="wrap" gap="sm">
              <Group gap="xs" wrap="wrap">
                <Text size="sm" fw={500}>
                  Match
                </Text>
                <Select
                  value={filterGroup.operator}
                  onChange={(value) =>
                    setFilterGroup({
                      ...filterGroup,
                      operator: value as "AND" | "OR",
                    })
                  }
                  data={[
                    { value: "AND", label: "ALL" },
                    { value: "OR", label: "ANY" },
                  ]}
                  styles={{ root: { width: 100 } }}
                  size="xs"
                />
                <Text size="sm" fw={500}>
                  of the following:
                </Text>
              </Group>
              <Button
                size="xs"
                variant="light"
                color="teal"
                leftSection={<IconPlus size={14} />}
                onClick={addCondition}
              >
                Add Condition
              </Button>
            </Group>

            <Divider />

            <ScrollArea style={{ flex: 1 }} offsetScrollbars>
              <Stack gap="sm" pr="xs">
                {filterGroup.conditions.length === 0 && (
                  <Paper
                    p="xl"
                    withBorder
                    style={{ backgroundColor: "#f8f9fa" }}
                  >
                    <Stack gap="sm" align="center">
                      <IconFilter
                        size={40}
                        color="var(--mantine-color-gray-4)"
                      />
                      <Text size="sm" c="dimmed" ta="center">
                        No conditions added yet
                      </Text>
                      <Text size="xs" c="dimmed" ta="center">
                        Click "Add Condition" above to start building your
                        filter
                      </Text>
                    </Stack>
                  </Paper>
                )}

                {filterGroup.conditions.map(
                  (condition: FilterCondition, index: number) => (
                    <ConditionRow
                      key={condition.id}
                      condition={condition}
                      index={index}
                      onUpdate={(updates: Partial<FilterCondition>) =>
                        updateCondition(condition.id, updates)
                      }
                      onRemove={() => removeCondition(condition.id)}
                      onCategoryChange={(category: FilterCategory) =>
                        handleCategoryChange(condition.id, category)
                      }
                      onFieldChange={(field: string) =>
                        handleFieldChange(condition.id, field)
                      }
                      getFieldsByCategory={getFieldsByCategory}
                      getFieldDefinition={getFieldDefinition}
                      categoryOptions={categoryOptions}
                      operatorLabels={operatorLabelsFromConfig}
                    />
                  ),
                )}
              </Stack>
            </ScrollArea>
          </>
        )}

        <Box>
          <Divider mb="md" />
          <Group justify="space-between" wrap="wrap" gap="sm">
            <Button
              variant="subtle"
              color="gray"
              onClick={handleClear}
              disabled={filterGroup.conditions.length === 0}
            >
              Clear All
            </Button>
            <Group gap="sm">
              <Button variant="default" onClick={onClose}>
                Cancel
              </Button>
              <Button
                color="teal"
                onClick={handleApply}
                disabled={filterGroup.conditions.length === 0}
              >
                Apply{" "}
                {filterGroup.conditions.length > 0 &&
                  `(${filterGroup.conditions.length})`}
              </Button>
            </Group>
          </Group>
        </Box>
      </Stack>
    </Modal>
  );
}
