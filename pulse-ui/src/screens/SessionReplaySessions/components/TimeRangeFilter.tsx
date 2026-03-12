import { Group, Text, Select } from "@mantine/core";
import { DateInput } from "@mantine/dates";
import { IconCalendar } from "@tabler/icons-react";
import {
  TIME_RANGE_OPTIONS,
  DEFAULT_DATE_PRESET,
  SESSION_LIST_LABELS,
} from "../constants/sessionList.constants";

export interface TimeRangeFilterProps {
  preset: string;
  from: string | null;
  to: string | null;
  onPresetChange: (preset: string) => void;
  onCustomRangeChange: (from: string | null, to: string | null) => void;
  onPageReset: () => void;
}

export function TimeRangeFilter({
  preset,
  from,
  to,
  onPresetChange,
  onCustomRangeChange,
  onPageReset,
}: TimeRangeFilterProps) {
  const isValidPreset = TIME_RANGE_OPTIONS.some((o) => o.value === preset);
  const value = isValidPreset ? preset : DEFAULT_DATE_PRESET;
  const isCustom = value === "custom";

  const handlePresetChange = (v: string | null) => {
    const next = v ?? DEFAULT_DATE_PRESET;
    onPresetChange(next);
    onPageReset();
  };

  const handleFromChange = (date: Date | null) => {
    onCustomRangeChange(date?.toISOString() ?? null, to);
    onPageReset();
  };

  const handleToChange = (date: Date | null) => {
    onCustomRangeChange(from, date?.toISOString() ?? null);
    onPageReset();
  };

  return (
    <Group gap="sm" align="center">
      <Text size="sm" fw={500} c="dimmed">
        {SESSION_LIST_LABELS.timeRangeLabel}
      </Text>
      <Select
        leftSection={<IconCalendar size={16} />}
        placeholder={SESSION_LIST_LABELS.timeRangePlaceholder}
        value={value}
        onChange={handlePresetChange}
        data={TIME_RANGE_OPTIONS.map((o) => ({
          value: o.value,
          label: o.label,
        }))}
        style={{ minWidth: 180 }}
        allowDeselect={false}
      />
      {isCustom && (
        <>
          <DateInput
            leftSection={<IconCalendar size={16} />}
            placeholder={SESSION_LIST_LABELS.fromDatePlaceholder}
            value={from ? new Date(from) : undefined}
            onChange={handleFromChange}
            maxDate={to ? new Date(to) : new Date()}
            style={{ minWidth: 160 }}
            clearable
          />
          <DateInput
            leftSection={<IconCalendar size={16} />}
            placeholder={SESSION_LIST_LABELS.toDatePlaceholder}
            value={to ? new Date(to) : undefined}
            onChange={handleToChange}
            minDate={from ? new Date(from) : undefined}
            maxDate={new Date()}
            style={{ minWidth: 160 }}
            clearable
          />
        </>
      )}
    </Group>
  );
}
