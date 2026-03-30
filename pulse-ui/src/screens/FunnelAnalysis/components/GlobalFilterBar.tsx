import { useState } from "react";
import { Box, Badge, ActionIcon, Group, Button, Popover, ScrollArea, TextInput, UnstyledButton, Text } from "@mantine/core";
import { IconX, IconCheck, IconSearch, IconChevronDown } from "@tabler/icons-react";
import classes from "../FunnelAnalysis.module.css";

export interface ActiveFilter {
  property: string;
  value: string;
}

interface GlobalFilterBarProps {
  filters: ActiveFilter[];
  onFiltersChange: (filters: ActiveFilter[]) => void;
  filterOptions: Record<string, string[]>;
  /** Extra spacing and larger filter controls (e.g. create funnel/journey wizard). */
  comfortable?: boolean;
  className?: string;
}

const PROPERTY_ICONS: Record<string, string> = {
  "OS Name": "🍏",
  "OS Version": "⚙️",
  "App Version": "📱",
};

function FilterDropdown({
  property,
  options,
  selectedValues,
  onToggle,
  buttonSize = "xs",
}: {
  property: string;
  options: string[];
  selectedValues: string[];
  onToggle: (value: string) => void;
  buttonSize?: "xs" | "sm";
}) {
  const [search, setSearch] = useState("");
  const filteredOptions = options.filter((o) =>
    o.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <Popover position="bottom-start" shadow="md" width={220} withArrow>
      <Popover.Target>
        <Button
          variant="default"
          size={buttonSize}
          radius="xl"
          rightSection={<IconChevronDown size={buttonSize === "sm" ? 14 : 12} />}
          style={{ fontWeight: 500 }}
        >
          {property} {selectedValues.length > 0 && `(${selectedValues.length})`}
        </Button>
      </Popover.Target>
      <Popover.Dropdown p={0}>
        <Box p="xs" style={{ borderBottom: "1px solid #eee" }}>
          <TextInput
            placeholder="Search..."
            size="xs"
            value={search}
            onChange={(e) => setSearch(e.currentTarget.value)}
            leftSection={<IconSearch size={12} />}
          />
        </Box>
        <ScrollArea h={200} type="scroll">
          <Box p="xs">
            {filteredOptions.length === 0 ? (
              <Text size="xs" c="dimmed" ta="center" py="sm">
                No options found
              </Text>
            ) : (
              filteredOptions.map((opt) => (
                <UnstyledButton
                  key={opt}
                  w="100%"
                  p="xs"
                  onClick={() => onToggle(opt)}
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    borderRadius: 4,
                  }}
                  className={classes.filterOption}
                >
                  <Text size="sm">{opt}</Text>
                  {selectedValues.includes(opt) && (
                    <IconCheck size={14} color="#0ba09a" />
                  )}
                </UnstyledButton>
              ))
            )}
          </Box>
        </ScrollArea>
      </Popover.Dropdown>
    </Popover>
  );
}

export function GlobalFilterBar({
  filters,
  onFiltersChange,
  filterOptions,
  comfortable = false,
  className,
}: GlobalFilterBarProps) {
  const removeFilter = (property: string, value: string) => {
    onFiltersChange(filters.filter((f) => !(f.property === property && f.value === value)));
  };

  const toggleFilter = (property: string, value: string) => {
    const exists = filters.some((f) => f.property === property && f.value === value);
    if (exists) {
      onFiltersChange(filters.filter((f) => !(f.property === property && f.value === value)));
    } else {
      onFiltersChange([...filters, { property, value }]);
    }
  };

  const groupedFilters = filters.reduce((acc, f) => {
    if (!acc[f.property]) acc[f.property] = [];
    acc[f.property].push(f.value);
    return acc;
  }, {} as Record<string, string[]>);

  const gap = comfortable ? "md" : "xs";
  const btnSize = comfortable ? "sm" : "xs";

  return (
    <Box
      className={[classes.filterBar, className].filter(Boolean).join(" ")}
    >
      <Group gap={gap} wrap="wrap" align="flex-start">
        {Object.entries(filterOptions).map(([property, options]) => {
          const selectedValues = filters
            .filter((f) => f.property === property)
            .map((f) => f.value);

          return (
            <FilterDropdown
              key={property}
              property={property}
              options={options}
              selectedValues={selectedValues}
              onToggle={(val) => toggleFilter(property, val)}
              buttonSize={btnSize}
            />
          );
        })}

        {filters.length > 0 && (
          <Box
            style={{
              width: 1,
              height: comfortable ? 28 : 24,
              backgroundColor: "#dee2e6",
              margin: comfortable ? "0 10px" : "0 8px",
            }}
          />
        )}

        {Object.entries(groupedFilters).map(([property, values]) => (
          <Group
            key={property}
            gap={gap}
            style={{
              border: "1px solid #e9ecef",
              borderRadius: "24px",
              padding: "4px 4px 4px 12px",
              backgroundColor: "#f8f9fa",
            }}
          >
            <Text size="xs" fw={600} c="dimmed" style={{ display: "flex", alignItems: "center", gap: "4px" }}>
              {PROPERTY_ICONS[property] || "🏷️"} {property}
            </Text>
            {values.map((value, index) => (
              <Badge
                key={`${property}-${value}-${index}`}
                variant="light"
                color="teal"
                size="sm"
                radius="xl"
                rightSection={
                  <ActionIcon
                    size="xs"
                    color="teal"
                    radius="xl"
                    variant="transparent"
                    onClick={() => removeFilter(property, value)}
                  >
                    <IconX size={10} />
                  </ActionIcon>
                }
                style={{ textTransform: "none" }}
              >
                {value}
              </Badge>
            ))}
          </Group>
        ))}
      </Group>
    </Box>
  );
}
