import { useState } from "react";
import { Box, Popover, Select, Text } from "@mantine/core";
import { IconPlus, IconX } from "@tabler/icons-react";
import { FILTER_OPTIONS } from "../mockData";
import classes from "../FunnelAnalysis.module.css";

export interface ActiveFilter {
  property: string;
  value: string;
}

interface GlobalFilterBarProps {
  filters: ActiveFilter[];
  onFiltersChange: (filters: ActiveFilter[]) => void;
}

const PROPERTY_ICONS: Record<string, string> = {
  OS: "🍏",
  "App Version": "📱",
  "Device Model": "💻",
  Country: "🌍",
  City: "🏙️",
};

export function GlobalFilterBar({
  filters,
  onFiltersChange,
}: GlobalFilterBarProps) {
  const [addOpen, setAddOpen] = useState(false);
  const [selectedProperty, setSelectedProperty] = useState<string | null>(null);

  const removeFilter = (index: number) => {
    onFiltersChange(filters.filter((_, i) => i !== index));
  };

  const addFilter = (value: string | null) => {
    if (selectedProperty && value) {
      onFiltersChange([
        ...filters,
        { property: selectedProperty, value },
      ]);
      setSelectedProperty(null);
      setAddOpen(false);
    }
  };

  const propertyOptions = Object.keys(FILTER_OPTIONS).map((p) => ({
    value: p,
    label: p,
  }));

  const valueOptions = selectedProperty
    ? FILTER_OPTIONS[selectedProperty].map((v) => ({ value: v, label: v }))
    : [];

  return (
    <Box className={classes.filterBar}>
      {filters.map((filter, index) => (
        <Box key={`${filter.property}-${filter.value}-${index}`} className={classes.filterPill}>
          <span>{PROPERTY_ICONS[filter.property] || "🏷️"}</span>
          <span className={classes.filterPillLabel}>{filter.property}:</span>
          <span>{filter.value}</span>
          <Box
            className={classes.filterPillClose}
            onClick={() => removeFilter(index)}
          >
            <IconX size={10} />
          </Box>
        </Box>
      ))}

      <Popover
        opened={addOpen}
        onChange={setAddOpen}
        position="bottom-start"
        withArrow
        shadow="md"
        width={240}
      >
        <Popover.Target>
          <button
            className={classes.addFilterBtn}
            onClick={() => {
              setSelectedProperty(null);
              setAddOpen(!addOpen);
            }}
          >
            <IconPlus size={14} />
            Add Filter
          </button>
        </Popover.Target>
        <Popover.Dropdown p="sm">
          {!selectedProperty ? (
            <>
              <Text size="xs" fw={600} c="dimmed" mb="xs">
                Select property
              </Text>
              <Select
                data={propertyOptions}
                placeholder="Choose property..."
                size="xs"
                onChange={(val) => setSelectedProperty(val)}
                searchable
              />
            </>
          ) : (
            <>
              <Text size="xs" fw={600} c="dimmed" mb="xs">
                {selectedProperty} is
              </Text>
              <Select
                data={valueOptions}
                placeholder="Choose value..."
                size="xs"
                onChange={addFilter}
                searchable
              />
            </>
          )}
        </Popover.Dropdown>
      </Popover>
    </Box>
  );
}
