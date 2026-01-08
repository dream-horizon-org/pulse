import {
  Box,
  Text,
  Stack,
  Badge,
  TextInput,
  ScrollArea,
  Tooltip,
  UnstyledButton,
  Group,
} from "@mantine/core";
import { IconSearch, IconHash, IconLetterCase, IconCalendar, IconToggleLeft, IconBraces } from "@tabler/icons-react";
import { useState, useMemo } from "react";
import { FieldMetadata } from "../../RealTimeQuery.interface";
import classes from "./FieldsList.module.css";

interface FieldsListProps {
  fields: FieldMetadata[];
  onFieldClick?: (field: FieldMetadata) => void;
}

const TYPE_ICONS: Record<string, typeof IconHash> = {
  string: IconLetterCase,
  number: IconHash,
  date: IconCalendar,
  boolean: IconToggleLeft,
  object: IconBraces,
  array: IconBraces,
};

const TYPE_COLORS: Record<string, string> = {
  string: "blue",
  number: "green",
  date: "orange",
  boolean: "grape",
  object: "gray",
  array: "cyan",
};

export function FieldsList({ fields, onFieldClick }: FieldsListProps) {
  const [searchQuery, setSearchQuery] = useState("");

  const filteredFields = useMemo(() => {
    if (!searchQuery.trim()) return fields;
    const query = searchQuery.toLowerCase();
    return fields.filter(
      (f) =>
        f.name.toLowerCase().includes(query) ||
        f.description?.toLowerCase().includes(query)
    );
  }, [fields, searchQuery]);

  return (
    <Box className={classes.container}>
      <Stack gap="xs">
        <TextInput
          size="xs"
          placeholder="Search fields..."
          leftSection={<IconSearch size={14} />}
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className={classes.searchInput}
        />
        
        <ScrollArea h={280} type="auto" offsetScrollbars>
          <Stack gap={4}>
            {filteredFields.length === 0 ? (
              <Text size="xs" c="dimmed" ta="center" py="md">
                No fields found
              </Text>
            ) : (
              filteredFields.map((field) => {
                const TypeIcon = TYPE_ICONS[field.type] || IconLetterCase;
                const color = TYPE_COLORS[field.type] || "gray";

                return (
                  <Tooltip
                    key={field.name}
                    label={field.description || field.name}
                    position="right"
                    withArrow
                    multiline
                    w={200}
                  >
                    <UnstyledButton
                      className={classes.fieldItem}
                      onClick={() => onFieldClick?.(field)}
                    >
                      <Group gap="xs" wrap="nowrap">
                        <TypeIcon size={14} stroke={1.5} color={`var(--mantine-color-${color}-5)`} />
                        <Text size="xs" className={classes.fieldName} truncate>
                          {field.name}
                        </Text>
                        <Badge size="xs" variant="light" color={color} className={classes.typeBadge}>
                          {field.type}
                        </Badge>
                      </Group>
                    </UnstyledButton>
                  </Tooltip>
                );
              })
            )}
          </Stack>
        </ScrollArea>
        
        <Text size="xs" c="dimmed" ta="center">
          {filteredFields.length} of {fields.length} fields
        </Text>
      </Stack>
    </Box>
  );
}

