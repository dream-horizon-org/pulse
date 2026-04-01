import { useEffect, useMemo, useState } from "react";
import { Group, Select, Stack, Text } from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import {
  getDateFromUTCTimeString,
  getStartAndEndDateTimeString,
  getUTCDateTimeStringFromDateValue,
  isValidUtcWallClockString,
} from "../../../utils/DateUtil";
import type { HeatmapTimeRangePopoverBodyProps } from "./heatmapFilterPanel.types";
import {
  getHeatmapQuickTimeSelectData,
  HEATMAP_TIME_PRESET_CUSTOM,
  HEATMAP_TIME_RANGE_SUBTRACT_MINUTES,
  inferHeatmapTimePreset,
} from "./heatmapTimePresets";
import filterClasses from "../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.module.css";

/**
 * Inner content of the time popover: quick presets and optional From / To.
 */
export function HeatmapTimeRangePopoverBody({
  opened,
  value,
  onChange,
}: HeatmapTimeRangePopoverBodyProps) {
  const heatmapTimeSelectData = useMemo(() => getHeatmapQuickTimeSelectData(), []);

  const [timePreset, setTimePreset] = useState<string>(HEATMAP_TIME_PRESET_CUSTOM);

  useEffect(() => {
    if (!opened) {
      return;
    }
    setTimePreset(inferHeatmapTimePreset(value.startTime, value.endTime));
  }, [opened, value.startTime, value.endTime]);

  let startDate: Date | null = null;
  if (value.startTime?.trim() && isValidUtcWallClockString(value.startTime)) {
    startDate = getDateFromUTCTimeString(value.startTime);
  }
  let endDate: Date | null = null;
  if (value.endTime?.trim() && isValidUtcWallClockString(value.endTime)) {
    endDate = getDateFromUTCTimeString(value.endTime);
  }

  return (
    <Stack gap="sm">
      <Text size="xs" fw={700} c="dark">
        Date &amp; time (UTC)
      </Text>
      <Select
        label="Quick range"
        size="xs"
        className={filterClasses.filterInput}
        data={heatmapTimeSelectData}
        value={timePreset}
        onChange={(v) => {
          const next = v ?? HEATMAP_TIME_PRESET_CUSTOM;
          setTimePreset(next);
          if (next !== HEATMAP_TIME_PRESET_CUSTOM) {
            const { startDate: st, endDate: et } = getStartAndEndDateTimeString(
              next,
              HEATMAP_TIME_RANGE_SUBTRACT_MINUTES,
            );
            onChange({ ...value, startTime: st, endTime: et });
          }
        }}
      />
      {timePreset === HEATMAP_TIME_PRESET_CUSTOM ? (
        <Group align="flex-end" wrap="wrap" gap="md">
          <Stack gap={4} style={{ minWidth: 180 }}>
            <Text size="xs" fw={600}>
              From
            </Text>
            <DateTimePicker
              value={startDate}
              onChange={(d) => {
                setTimePreset(HEATMAP_TIME_PRESET_CUSTOM);
                onChange({
                  ...value,
                  startTime: getUTCDateTimeStringFromDateValue(d),
                });
              }}
              size="xs"
              clearable
              valueFormat="MMM D, YYYY HH:mm"
              maxDate={endDate ?? undefined}
            />
          </Stack>
          <Stack gap={4} style={{ minWidth: 180 }}>
            <Text size="xs" fw={600}>
              To
            </Text>
            <DateTimePicker
              value={endDate}
              onChange={(d) => {
                setTimePreset(HEATMAP_TIME_PRESET_CUSTOM);
                onChange({
                  ...value,
                  endTime: getUTCDateTimeStringFromDateValue(d),
                });
              }}
              size="xs"
              clearable
              valueFormat="MMM D, YYYY HH:mm"
              minDate={startDate ?? undefined}
            />
          </Stack>
        </Group>
      ) : null}
    </Stack>
  );
}
