import { useState, useMemo } from 'react';
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
} from '@mantine/core';
import {
  IconPlus,
  IconFilter,
  IconInfoCircle,
} from '@tabler/icons-react';
import {
  FilterCondition,
  FilterGroup,
  FilterCategory,
  FilterFieldDefinition,
  FilterOperator,
} from '../../../services/sessionReplay/filterConfig';
import { v4 as uuidv4 } from 'uuid';
import { ConditionRow } from './ConditionRow';
import { useFilterSchema } from '../hooks/useFilterSchema';

interface AdvancedFilterBuilderProps {
  opened: boolean;
  onClose: () => void;
  onApply: (filterGroup: FilterGroup) => void;
  initialFilters?: FilterGroup;
}

export function AdvancedFilterBuilder({
  opened,
  onClose,
  onApply,
  initialFilters,
}: AdvancedFilterBuilderProps) {
  // Fetch filter schema from API
  const { schema, loading: schemaLoading } = useFilterSchema();
  
  const [filterGroup, setFilterGroup] = useState<FilterGroup>(
    initialFilters ?? {
      id: uuidv4(),
      operator: 'AND',
      conditions: [],
    }
  );

  // Helper functions using API schema
  const getFieldsByCategory = (category: FilterCategory): FilterFieldDefinition[] => {
    if (!schema) return [];
    const categoryData = schema.categories.find((c) => c.key === category);
    if (!categoryData) return [];
    
    // Convert API types to UI types
    return categoryData.fields.map((field) => ({
      ...field,
      category: field.category as FilterCategory,
      operators: field.operators as FilterOperator[],
    }));
  };

  const getFieldDefinition = (fieldKey: string): FilterFieldDefinition | null => {
    if (!schema) return null;
    for (const category of schema.categories) {
      const field = category.fields.find((f) => f.key === fieldKey);
      if (field) {
        return {
          ...field,
          category: field.category as FilterCategory,
          operators: field.operators as FilterOperator[],
        };
      }
    }
    return null;
  };

  // Category options from API
  const categoryOptions = useMemo(() => {
    if (!schema) return [];
    return schema.categories.map((cat) => ({
      value: cat.key,
      label: cat.label,
    }));
  }, [schema]);

  const addCondition = () => {
    const newCondition: FilterCondition = {
      id: uuidv4(),
      category: 'ui_interaction',
      field: 'interaction.type',
      operator: 'equals',
      value: '',
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
        condition.id === id ? { ...condition, ...updates } : condition
      ),
    });
  };

  const removeCondition = (id: string) => {
    setFilterGroup({
      ...filterGroup,
      conditions: filterGroup.conditions.filter((condition: FilterCondition) => condition.id !== id),
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
        value: firstField.type === 'boolean' ? true : '',
      });
    }
  };

  const handleFieldChange = (id: string, fieldKey: string) => {
    const fieldDef = getFieldDefinition(fieldKey);
    
    if (fieldDef) {
      updateCondition(id, {
        field: fieldKey,
        operator: fieldDef.operators[0],
        value: fieldDef.type === 'boolean' ? true : '',
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
      operator: 'AND',
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
          <Tooltip label="Build complex queries with multiple conditions" position="right">
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
          maxHeight: 'calc(100vh - 200px)',
          display: 'flex',
          flexDirection: 'column',
        },
      }}
    >
      <Stack gap="md" style={{ flex: 1, minHeight: 0 }}>
        {schemaLoading ? (
          <Stack align="center" justify="center" py="xl">
            <Loader size="lg" />
            <Text size="sm" c="dimmed">Loading filter options...</Text>
          </Stack>
        ) : !schema ? (
          <Paper p="xl" withBorder style={{ backgroundColor: '#fff3cd' }}>
            <Text size="sm" c="red" ta="center">
              Failed to load filter schema. Please try again.
            </Text>
          </Paper>
        ) : (
          <>
        <Group justify="space-between" wrap="wrap" gap="sm">
          <Group gap="xs" wrap="wrap">
            <Text size="sm" fw={500}>Match</Text>
            <Select
              value={filterGroup.operator}
              onChange={(value) =>
                setFilterGroup({ ...filterGroup, operator: value as 'AND' | 'OR' })
              }
              data={[
                { value: 'AND', label: 'ALL' },
                { value: 'OR', label: 'ANY' },
              ]}
              styles={{ root: { width: 100 } }}
              size="xs"
            />
            <Text size="sm" fw={500}>of the following:</Text>
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
              <Paper p="xl" withBorder style={{ backgroundColor: '#f8f9fa' }}>
                <Stack gap="sm" align="center">
                  <IconFilter size={40} color="var(--mantine-color-gray-4)" />
                  <Text size="sm" c="dimmed" ta="center">
                    No conditions added yet
                  </Text>
                  <Text size="xs" c="dimmed" ta="center">
                    Click "Add Condition" above to start building your filter
                  </Text>
                </Stack>
              </Paper>
            )}

            {filterGroup.conditions.map((condition: FilterCondition, index: number) => (
              <ConditionRow
                key={condition.id}
                condition={condition}
                index={index}
                onUpdate={(updates: Partial<FilterCondition>) => updateCondition(condition.id, updates)}
                onRemove={() => removeCondition(condition.id)}
                onCategoryChange={(category: FilterCategory) => handleCategoryChange(condition.id, category)}
                onFieldChange={(field: string) => handleFieldChange(condition.id, field)}
                getFieldsByCategory={getFieldsByCategory}
                getFieldDefinition={getFieldDefinition}
                categoryOptions={categoryOptions}
              />
            ))}
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
                Apply {filterGroup.conditions.length > 0 && `(${filterGroup.conditions.length})`}
              </Button>
            </Group>
          </Group>
        </Box>
      </Stack>
    </Modal>
  );
}
