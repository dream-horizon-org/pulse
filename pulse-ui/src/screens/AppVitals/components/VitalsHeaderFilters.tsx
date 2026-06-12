import { Popover, Button, Stack, Badge, Group, Select } from "@mantine/core";
import { IconFilter } from "@tabler/icons-react";
import { useState } from "react";
import { APP_VERSIONS, OS_VERSIONS, DEVICES } from "../AppVitals.constants";
import classes from "./VitalsHeaderFilters.module.css";

interface VitalsHeaderFiltersProps {
  appVersion: string;
  onAppVersionChange: (value: string | null) => void;
  osVersion: string;
  onOsVersionChange: (value: string | null) => void;
  device: string;
  onDeviceChange: (value: string | null) => void;
}

export const VitalsHeaderFilters: React.FC<VitalsHeaderFiltersProps> = ({
  appVersion,
  onAppVersionChange,
  osVersion,
  onOsVersionChange,
  device,
  onDeviceChange,
}) => {
  const [opened, setOpened] = useState(false);

  const getActiveFilterCount = () => {
    let count = 0;
    if (appVersion !== "all") count++;
    if (osVersion !== "all") count++;
    if (device !== "all") count++;
    return count;
  };

  const activeCount = getActiveFilterCount();

  const handleClearAll = () => {
    onAppVersionChange("all");
    onOsVersionChange("all");
    onDeviceChange("all");
  };

  return (
    <Popover
      opened={opened}
      onChange={setOpened}
      width={350}
      position="bottom-end"
      withArrow
      shadow="md"
      closeOnEscape
      closeOnClickOutside
    >
      <Popover.Target>
        <Button
          variant="transparent"
          size="compact-sm"
          onClick={() => setOpened((o) => !o)}
          className={classes.filterButton}
        >
          <Group gap={6} wrap="nowrap">
            <IconFilter size={14} stroke={2.5} />
            <span>Filters</span>
            {activeCount > 0 && (
              <Badge size="xs" circle className={classes.filterBadge}>
                {activeCount}
              </Badge>
            )}
          </Group>
        </Button>
      </Popover.Target>
      <Popover.Dropdown className={classes.filterDropdown}>
        <Stack gap="xs">
          <Select
            label="App Version"
            value={appVersion}
            onChange={onAppVersionChange}
            data={APP_VERSIONS}
            clearable
            size="xs"
            className={classes.filterInput}
            placeholder="Select version"
          />
          <Select
            label="OS Version"
            value={osVersion}
            onChange={onOsVersionChange}
            data={OS_VERSIONS}
            clearable
            size="xs"
            className={classes.filterInput}
            placeholder="Select OS version"
          />
          <Select
            label="Device"
            value={device}
            onChange={onDeviceChange}
            data={DEVICES}
            clearable
            size="xs"
            className={classes.filterInput}
            placeholder="Select device"
          />
          {activeCount > 0 && (
            <Button
              variant="subtle"
              size="xs"
              onClick={handleClearAll}
              className={classes.clearButton}
            >
              Clear All Filters
            </Button>
          )}
        </Stack>
      </Popover.Dropdown>
    </Popover>
  );
};
