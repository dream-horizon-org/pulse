import { Button, Select, Box, Text } from "@mantine/core";
import { useMemo, useState } from "react";
import { AlertFiltersProps } from "./AlertFilters.interface";
import classes from "./AlertFilters.module.css";

export function AlertFilters({
  options,
  scopeLabels,
  onFilterSave,
  onFilterReset,
  created_by,
  scope,
  updated_by,
}: AlertFiltersProps) {
  const [selectedCreatedBy, setSelectedCreatedBy] = useState<string | null>(
    created_by,
  );
  const [selectedScope, setSelectedScope] = useState<string | null>(scope);
  const [selectedUpdatedBy, setSelectedUpdatedBy] = useState<string | null>(
    updated_by,
  );

  // Transform scope options to include labels
  const scopeOptions = useMemo(() => {
    return options.scope.map((s) => ({
      value: s,
      label: scopeLabels[s] || s,
    }));
  }, [options.scope, scopeLabels]);

  const handleApply = () => {
    onFilterSave({
      created_by: selectedCreatedBy,
      scope: selectedScope,
      updated_by: selectedUpdatedBy,
    });
  };

  const handleReset = () => {
    setSelectedCreatedBy(null);
    setSelectedScope(null);
    setSelectedUpdatedBy(null);
    onFilterReset();
  };

  return (
    <Box className={classes.filtersContainer}>
      <Box className={classes.filterItem}>
        <Text className={classes.filterLabel}>Created By</Text>
        <Select
          placeholder="Select creator"
          data={options.created_by}
          value={selectedCreatedBy}
          onChange={setSelectedCreatedBy}
          clearable
          size="sm"
        />
      </Box>

      <Box className={classes.filterItem}>
        <Text className={classes.filterLabel}>Scope</Text>
        <Select
          placeholder="Select scope"
          data={scopeOptions}
          value={selectedScope}
          onChange={setSelectedScope}
          clearable
          size="sm"
        />
      </Box>

      <Box className={classes.filterItem}>
        <Text className={classes.filterLabel}>Updated By</Text>
        <Select
          placeholder="Select updater"
          data={options.updated_by}
          value={selectedUpdatedBy}
          onChange={setSelectedUpdatedBy}
          clearable
          size="sm"
        />
      </Box>

      <Box className={classes.buttonGroup}>
        <Button variant="subtle" size="xs" onClick={handleReset}>
          Reset
        </Button>
        <Button size="xs" onClick={handleApply}>
          Apply
        </Button>
      </Box>
    </Box>
  );
}


