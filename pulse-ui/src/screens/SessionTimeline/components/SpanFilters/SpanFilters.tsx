import { Paper, TextInput } from "@mantine/core";
import { IconSearch } from "@tabler/icons-react";
import classes from "./SpanFilters.module.css";

interface SpanFiltersProps {
  searchQuery: string;
  onSearchChange: (value: string) => void;
}

export function SpanFilters({ searchQuery, onSearchChange }: SpanFiltersProps) {
  return (
    <Paper className={classes.filtersSection} p="md" mb="md">
      <TextInput
        placeholder="Search spans, logs, and events by name or attributes..."
        leftSection={<IconSearch size={16} />}
        value={searchQuery}
        onChange={(e) => onSearchChange(e.target.value)}
      />
    </Paper>
  );
}
