import {
  Box,
  Button,
  Group,
  Select,
  ActionIcon,
  Stack,
  Paper,
  Text,
  Badge,
  NumberInput,
} from "@mantine/core";
import { IconPlus, IconX, IconLayoutGrid } from "@tabler/icons-react";
import { v4 as uuidV4 } from "uuid";
import { GroupByDimension, FieldMetadata, DateGranularity } from "../../RealTimeQuery.interface";
import { DATE_GRANULARITIES } from "../../RealTimeQuery.constants";
import classes from "./GroupBySelector.module.css";

interface GroupBySelectorProps {
  dimensions: GroupByDimension[];
  fields: FieldMetadata[];
  onChange: (dimensions: GroupByDimension[]) => void;
}

export function GroupBySelector({ dimensions, fields, onChange }: GroupBySelectorProps) {
  const fieldOptions = fields.map((f) => ({
    value: f.name,
    label: f.name,
    type: f.type,
  }));

  const isDateField = (fieldName: string) => {
    const field = fields.find((f) => f.name === fieldName);
    return field?.type === "date";
  };

  const addDimension = () => {
    const newDimension: GroupByDimension = {
      id: uuidV4(),
      field: fields[0]?.name || "",
      granularity: undefined,
      limit: undefined,
    };
    onChange([...dimensions, newDimension]);
  };

  const updateDimension = (id: string, updates: Partial<GroupByDimension>) => {
    onChange(
      dimensions.map((d) => {
        if (d.id !== id) return d;
        const updated = { ...d, ...updates };
        // Add/remove granularity based on field type
        if (updates.field) {
          if (isDateField(updates.field)) {
            updated.granularity = updated.granularity || "day";
          } else {
            updated.granularity = undefined;
          }
        }
        return updated;
      })
    );
  };

  const removeDimension = (id: string) => {
    onChange(dimensions.filter((d) => d.id !== id));
  };

  return (
    <Box className={classes.container}>
      {dimensions.length === 0 ? (
        <Paper className={classes.emptyState} p="md" withBorder>
          <Stack align="center" gap="xs">
            <IconLayoutGrid size={24} stroke={1.5} color="var(--mantine-color-gray-5)" />
            <Text size="sm" c="dimmed" ta="center">
              No grouping applied. Add dimensions to segment your data.
            </Text>
            <Button
              variant="light"
              color="teal"
              size="xs"
              leftSection={<IconPlus size={14} />}
              onClick={addDimension}
            >
              Add Dimension
            </Button>
          </Stack>
        </Paper>
      ) : (
        <Stack gap="sm">
          {dimensions.map((dimension, index) => (
            <Paper key={dimension.id} className={classes.dimensionRow} p="xs" withBorder>
              <Group gap="xs" wrap="nowrap">
                <Badge
                  size="sm"
                  variant="light"
                  color="violet"
                  className={classes.indexBadge}
                >
                  {index + 1}
                </Badge>
                <Select
                  size="xs"
                  placeholder="Field"
                  value={dimension.field}
                  onChange={(value) => updateDimension(dimension.id, { field: value || "" })}
                  data={fieldOptions}
                  className={classes.fieldSelect}
                  searchable
                  comboboxProps={{ withinPortal: true }}
                />
                {isDateField(dimension.field) && (
                  <>
                    <Text size="xs" c="dimmed">by</Text>
                    <Select
                      size="xs"
                      placeholder="Granularity"
                      value={dimension.granularity}
                      onChange={(value) =>
                        updateDimension(dimension.id, {
                          granularity: value as DateGranularity,
                        })
                      }
                      data={DATE_GRANULARITIES.map((g) => ({
                        value: g.value,
                        label: g.label,
                      }))}
                      className={classes.granularitySelect}
                      comboboxProps={{ withinPortal: true }}
                    />
                  </>
                )}
                <NumberInput
                  size="xs"
                  placeholder="Limit"
                  value={dimension.limit}
                  onChange={(value) =>
                    updateDimension(dimension.id, {
                      limit: typeof value === "number" ? value : undefined,
                    })
                  }
                  min={1}
                  max={1000}
                  className={classes.limitInput}
                />
                <ActionIcon
                  size="sm"
                  variant="subtle"
                  color="red"
                  onClick={() => removeDimension(dimension.id)}
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
            onClick={addDimension}
            className={classes.addButton}
          >
            Add Dimension
          </Button>
        </Stack>
      )}
    </Box>
  );
}

