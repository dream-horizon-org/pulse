/**
 * TimeRangeSelector Component
 * Mandatory time range selector for query builder
 */

import {
  Box,
  Group,
  Text,
  Select,
  Stack,
} from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import { IconClock, IconCalendar } from "@tabler/icons-react";
import { TimeRange, TimeRangePreset, TIME_RANGE_PRESETS } from "../QueryBuilder.interface";
import classes from "./TimeRangeSelector.module.css";

interface TimeRangeSelectorProps {
  value: TimeRange;
  onChange: (value: TimeRange) => void;
}

export function TimeRangeSelector({ value, onChange }: TimeRangeSelectorProps) {
  const handlePresetChange = (preset: string | null) => {
    if (!preset) return;
    
    onChange({
      ...value,
      preset: preset as TimeRangePreset,
      // Clear custom dates when switching to preset
      ...(preset !== "custom" ? { startDate: undefined, endDate: undefined } : {}),
    });
  };

  const handleStartDateChange = (date: Date | null) => {
    onChange({
      ...value,
      startDate: date || undefined,
    });
  };

  const handleEndDateChange = (date: Date | null) => {
    onChange({
      ...value,
      endDate: date || undefined,
    });
  };

  const selectData = TIME_RANGE_PRESETS.map((preset) => ({
    value: preset.value,
    label: preset.label,
  }));

  return (
    <Box className={classes.container}>
      <Group gap="xs" mb="sm">
        <Box className={classes.iconWrapper}>
          <IconClock size={14} />
        </Box>
        <Text size="sm" fw={600}>Time Range</Text>
        <Text size="xs" c="red" fw={500}>(Required)</Text>
      </Group>

      <Stack gap="sm">
        <Select
          placeholder="Select time range"
          data={selectData}
          value={value.preset}
          onChange={handlePresetChange}
          leftSection={<IconCalendar size={14} />}
          size="sm"
          classNames={{ input: classes.select }}
        />

        {value.preset === "custom" && (
          <Box className={classes.customDateRange}>
            <Stack gap="xs">
              <DateTimePicker
                label="Start Date & Time"
                placeholder="Select start date"
                value={value.startDate || null}
                onChange={handleStartDateChange}
                size="sm"
                maxDate={value.endDate || new Date()}
                clearable
              />
              <DateTimePicker
                label="End Date & Time"
                placeholder="Select end date"
                value={value.endDate || null}
                onChange={handleEndDateChange}
                size="sm"
                minDate={value.startDate}
                maxDate={new Date()}
                clearable
              />
            </Stack>
          </Box>
        )}

        {/* Preview */}
        <Box className={classes.preview}>
          <Text size="xs" c="dimmed">
            {value.preset === "custom" ? (
              value.startDate && value.endDate ? (
                <>From {value.startDate.toLocaleString()} to {value.endDate.toLocaleString()}</>
              ) : (
                "Select start and end dates"
              )
            ) : (
              TIME_RANGE_PRESETS.find((p) => p.value === value.preset)?.label || ""
            )}
          </Text>
        </Box>
      </Stack>
    </Box>
  );
}

