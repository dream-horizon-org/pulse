import { Box, Select, Group, Text, Stack, Button, Popover } from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import { IconCalendar, IconClock } from "@tabler/icons-react";
import { useState } from "react";
import { TimeRange } from "../../RealTimeQuery.interface";
import { TIME_RANGE_PRESETS } from "../../RealTimeQuery.constants";
import classes from "./TimeRangeSelector.module.css";

interface TimeRangeSelectorProps {
  value: TimeRange;
  onChange: (value: TimeRange) => void;
}

export function TimeRangeSelector({ value, onChange }: TimeRangeSelectorProps) {
  const [customPopoverOpened, setCustomPopoverOpened] = useState(false);
  const [tempStartDate, setTempStartDate] = useState<Date | null>(value.startDate || null);
  const [tempEndDate, setTempEndDate] = useState<Date | null>(value.endDate || null);

  const handlePresetChange = (preset: string | null) => {
    if (!preset) return;
    if (preset === "CUSTOM") {
      setCustomPopoverOpened(true);
    } else {
      onChange({
        type: "preset",
        preset,
      });
    }
  };

  const handleApplyCustomRange = () => {
    if (tempStartDate && tempEndDate) {
      onChange({
        type: "custom",
        startDate: tempStartDate,
        endDate: tempEndDate,
      });
      setCustomPopoverOpened(false);
    }
  };

  const getDisplayValue = () => {
    if (value.type === "custom" && value.startDate && value.endDate) {
      return "CUSTOM";
    }
    return value.preset;
  };

  const getCustomRangeLabel = () => {
    if (value.type === "custom" && value.startDate && value.endDate) {
      const start = value.startDate.toLocaleDateString();
      const end = value.endDate.toLocaleDateString();
      return `${start} - ${end}`;
    }
    return null;
  };

  const selectData = [
    ...TIME_RANGE_PRESETS,
    { value: "CUSTOM", label: "Custom Range..." },
  ];

  return (
    <Box className={classes.container}>
      <Group gap="xs" wrap="nowrap">
        <IconClock size={16} stroke={1.5} color="var(--mantine-color-gray-6)" />
        <Popover
          opened={customPopoverOpened}
          onChange={setCustomPopoverOpened}
          position="bottom-start"
          withArrow
          shadow="md"
        >
          <Popover.Target>
            <Select
              size="xs"
              placeholder="Select time range"
              value={getDisplayValue()}
              onChange={handlePresetChange}
              data={selectData}
              className={classes.select}
              comboboxProps={{ withinPortal: true }}
              rightSection={<IconCalendar size={14} />}
            />
          </Popover.Target>
          <Popover.Dropdown>
            <Stack gap="sm" p="xs">
              <Text size="sm" fw={600}>Custom Date Range</Text>
              <DateTimePicker
                label="Start Date"
                size="xs"
                value={tempStartDate}
                onChange={setTempStartDate}
                maxDate={tempEndDate || undefined}
                clearable
              />
              <DateTimePicker
                label="End Date"
                size="xs"
                value={tempEndDate}
                onChange={setTempEndDate}
                minDate={tempStartDate || undefined}
                maxDate={new Date()}
                clearable
              />
              <Group justify="flex-end" gap="xs">
                <Button
                  size="xs"
                  variant="subtle"
                  onClick={() => setCustomPopoverOpened(false)}
                >
                  Cancel
                </Button>
                <Button
                  size="xs"
                  color="teal"
                  onClick={handleApplyCustomRange}
                  disabled={!tempStartDate || !tempEndDate}
                >
                  Apply
                </Button>
              </Group>
            </Stack>
          </Popover.Dropdown>
        </Popover>
        {getCustomRangeLabel() && (
          <Text size="xs" c="dimmed">
            {getCustomRangeLabel()}
          </Text>
        )}
      </Group>
    </Box>
  );
}

