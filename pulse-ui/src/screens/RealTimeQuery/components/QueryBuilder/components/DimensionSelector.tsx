/**
 * DimensionSelector Component
 * Select columns for GROUP BY
 */

import {
  Box,
  Group,
  Text,
  Select,
  ActionIcon,
  Button,
  Stack,
  Tooltip,
} from "@mantine/core";
import { IconPlus, IconTrash, IconLayoutGrid } from "@tabler/icons-react";
import { Dimension } from "../QueryBuilder.interface";
import classes from "./DimensionSelector.module.css";

interface DimensionSelectorProps {
  dimensions: Dimension[];
  availableColumns: { name: string; type: string }[];
  onChange: (dimensions: Dimension[]) => void;
}

// Generate unique ID
const generateId = () => `dim_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

export function DimensionSelector({ dimensions, availableColumns, onChange }: DimensionSelectorProps) {
  const handleAddDimension = () => {
    const newDimension: Dimension = {
      id: generateId(),
      column: "",
    };
    onChange([...dimensions, newDimension]);
  };

  const handleRemoveDimension = (id: string) => {
    onChange(dimensions.filter((d) => d.id !== id));
  };

  const handleDimensionChange = (id: string, column: string) => {
    onChange(
      dimensions.map((d) => (d.id === id ? { ...d, column } : d))
    );
  };

  // Get available columns (excluding already selected ones)
  const getAvailableColumnsFor = (currentId: string) => {
    const selectedColumns = dimensions
      .filter((d) => d.id !== currentId)
      .map((d) => d.column);
    
    return availableColumns
      .filter((c) => !selectedColumns.includes(c.name))
      .map((c) => ({ value: c.name, label: `${c.name} (${c.type})` }));
  };

  return (
    <Box className={classes.container}>
      <Group justify="space-between" mb="sm">
        <Group gap="xs">
          <Box className={classes.iconWrapper}>
            <IconLayoutGrid size={14} />
          </Box>
          <Text size="sm" fw={600}>Group By</Text>
          <Text size="xs" c="dimmed">({dimensions.length})</Text>
        </Group>
        <Tooltip label="Add dimension">
          <ActionIcon
            variant="light"
            color="violet"
            size="sm"
            onClick={handleAddDimension}
          >
            <IconPlus size={14} />
          </ActionIcon>
        </Tooltip>
      </Group>

      {dimensions.length === 0 ? (
        <Box className={classes.emptyState}>
          <Text size="xs" c="dimmed" ta="center">
            No grouping. Results will be a single aggregated row.
          </Text>
          <Button
            variant="subtle"
            size="xs"
            color="violet"
            leftSection={<IconPlus size={12} />}
            onClick={handleAddDimension}
            mt="xs"
          >
            Add Group By
          </Button>
        </Box>
      ) : (
        <Stack gap="xs">
          {dimensions.map((dimension, index) => (
            <Box key={dimension.id} className={classes.dimensionRow}>
              <Group gap="xs" wrap="nowrap">
                <Text size="xs" c="dimmed" w={20} ta="center">
                  {index + 1}
                </Text>
                <Select
                  placeholder="Select column"
                  data={getAvailableColumnsFor(dimension.id)}
                  value={dimension.column}
                  onChange={(v) => handleDimensionChange(dimension.id, v || "")}
                  size="xs"
                  style={{ flex: 1 }}
                  searchable
                  classNames={{ input: classes.select }}
                />
                <Tooltip label="Remove">
                  <ActionIcon
                    variant="subtle"
                    color="red"
                    size="sm"
                    onClick={() => handleRemoveDimension(dimension.id)}
                  >
                    <IconTrash size={14} />
                  </ActionIcon>
                </Tooltip>
              </Group>
            </Box>
          ))}
        </Stack>
      )}
    </Box>
  );
}

