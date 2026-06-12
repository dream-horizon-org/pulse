import { Group, Select, Popover, Button, Stack, Badge } from "@mantine/core";
import { IconFilter } from "@tabler/icons-react";
import { useState, useMemo } from "react";
import DateTimeRangePicker from "../../CriticalInteractionDetails/components/DateTimeRangePicker/DateTimeRangePicker";
import { StartEndDateTimeType } from "../../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import { CRITICAL_INTERACTION_QUICK_TIME_FILTERS } from "../../../constants";
import classes from "./OccurrenceFilters.module.css";

interface OccurrenceFiltersProps {
  startTime: string;
  endTime: string;
  onTimeFilterChange: (value: StartEndDateTimeType) => void;
  appVersion: string;
  onAppVersionChange: (value: string | null) => void;
  osVersion: string;
  onOsVersionChange: (value: string | null) => void;
  device: string;
  onDeviceChange: (value: string | null) => void;
}

const APP_VERSIONS = [
  { value: "all", label: "All Versions" },
  { value: "2.4.0", label: "2.4.0" },
  { value: "2.3.5", label: "2.3.5" },
  { value: "2.3.0", label: "2.3.0" },
  { value: "2.2.0", label: "2.2.0" },
  { value: "2.1.0", label: "2.1.0" },
];

const OS_VERSIONS = [
  { value: "all", label: "All OS Versions" },
  { value: "android-13", label: "Android 13" },
  { value: "android-12", label: "Android 12" },
  { value: "android-11", label: "Android 11" },
  { value: "android-10", label: "Android 10" },
];

const DEVICES = [
  { value: "all", label: "All Devices" },
  { value: "samsung", label: "Samsung" },
  { value: "google", label: "Google Pixel" },
  { value: "oneplus", label: "OnePlus" },
  { value: "xiaomi", label: "Xiaomi" },
  { value: "oppo", label: "Oppo" },
];

export const OccurrenceFilters: React.FC<OccurrenceFiltersProps> = ({
  startTime,
  endTime,
  onTimeFilterChange,
  appVersion,
  onAppVersionChange,
  osVersion,
  onOsVersionChange,
  device,
  onDeviceChange,
}) => {
  const [opened, setOpened] = useState(false);

  const filters = useMemo(
    () => ({ appVersion, osVersion, device }),
    [appVersion, osVersion, device],
  );

  const getActiveFilterCount = () => {
    return Object.values(filters).filter((value) => value !== "all").length;
  };

  const activeCount = getActiveFilterCount();

  const handleClearAllFilters = () => {
    onAppVersionChange("all");
    onOsVersionChange("all");
    onDeviceChange("all");
  };

  return (
    <Group gap="md" wrap="nowrap" align="center">
      <DateTimeRangePicker
        handleTimefilterChange={onTimeFilterChange}
        selectedQuickTimeFilterIndex={3}
        defaultQuickTimeFilterString={
          CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_7_DAYS
        }
        defaultEndTime={endTime}
        defaultStartTime={startTime}
        showRefreshButton={true}
      />

      {/* Filters Popover */}
      <Popover
        opened={opened}
        onChange={setOpened}
        width={300}
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
              placeholder="All Versions"
              data={APP_VERSIONS}
              value={appVersion}
              onChange={onAppVersionChange}
              size="xs"
              className={classes.filterInput}
              clearable
            />
            <Select
              label="OS Version"
              placeholder="All OS Versions"
              data={OS_VERSIONS}
              value={osVersion}
              onChange={onOsVersionChange}
              size="xs"
              className={classes.filterInput}
              clearable
            />
            <Select
              label="Device"
              placeholder="All Devices"
              data={DEVICES}
              value={device}
              onChange={onDeviceChange}
              size="xs"
              className={classes.filterInput}
              clearable
            />
            {activeCount > 0 && (
              <Button
                variant="light"
                color="red"
                size="xs"
                onClick={handleClearAllFilters}
                mt="sm"
              >
                Clear All Filters
              </Button>
            )}
          </Stack>
        </Popover.Dropdown>
      </Popover>
    </Group>
  );
};
