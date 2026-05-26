import { useEffect, useMemo, useRef, useState } from "react";
import { Button, Divider, Group, Select, Stack, Text } from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import {
  getStartAndEndDateTimeString,
  getDateTimeStringFromDateValue,
  isValidIstWallClockString,
} from "../../../utils/DateUtil";
import type { HeatmapTimeRangePopoverBodyProps } from "./heatmap.ui.types";
import { formatHeatmapCustomDateRangeLabel } from "./heatmapFilterPanelUtils";
import {
  HEATMAP_QUICK_TIME_PRESETS,
  HEATMAP_TIME_PRESET_CUSTOM,
  HEATMAP_TIME_RANGE_SUBTRACT_MINUTES,
  inferHeatmapTimePreset,
} from "./heatmapTimePresets";
import filterClasses from "../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.module.css";
import dayjs from "dayjs";

const PICK_DATES_LABEL = "Pick dates…";

/**
 * Inner content of the time popover: quick presets and optional From / To.
 * Uses local state for staging changes; only commits via onChange on Apply.
 * All times are stored and displayed in IST format (YYYY-MM-DD HH:mm:ss).
 */
export function HeatmapTimeRangePopoverBody({
  opened,
  value,
  onChange,
  onApply,
}: HeatmapTimeRangePopoverBodyProps) {
  const preferCustomRangeRef = useRef(false);

  const [timePreset, setTimePreset] = useState<string>(HEATMAP_TIME_PRESET_CUSTOM);
  const [stagedStartTime, setStagedStartTime] = useState(value.startTime);
  const [stagedEndTime, setStagedEndTime] = useState(value.endTime);

  useEffect(() => {
    if (!opened) {
      return;
    }
    const inferred = inferHeatmapTimePreset(value.startTime, value.endTime);
    if (
      preferCustomRangeRef.current &&
      inferred !== HEATMAP_TIME_PRESET_CUSTOM
    ) {
      setTimePreset(HEATMAP_TIME_PRESET_CUSTOM);
      return;
    }
    if (inferred !== HEATMAP_TIME_PRESET_CUSTOM) {
      preferCustomRangeRef.current = false;
    }
    setTimePreset(inferred);
    setStagedStartTime(value.startTime);
    setStagedEndTime(value.endTime);
  }, [opened, value.startTime, value.endTime]);

  const selectData = useMemo(() => {
    const rangeText = formatHeatmapCustomDateRangeLabel({
      startTime: stagedStartTime,
      endTime: stagedEndTime,
    });
    const customLabel =
      timePreset === HEATMAP_TIME_PRESET_CUSTOM && rangeText
        ? rangeText
        : PICK_DATES_LABEL;
    return [
      ...HEATMAP_QUICK_TIME_PRESETS.map((p) => ({
        value: p.value,
        label: p.label,
      })),
      { value: HEATMAP_TIME_PRESET_CUSTOM, label: customLabel },
    ];
  }, [timePreset, stagedStartTime, stagedEndTime]);

  let startDate: Date | null = null;
  if (stagedStartTime?.trim() && isValidIstWallClockString(stagedStartTime)) {
    // Parse IST time string to Date object
    // Format: "YYYY-MM-DD HH:mm:ss"
    const parsed = dayjs(stagedStartTime, "YYYY-MM-DD HH:mm:ss");
    if (parsed.isValid()) {
      startDate = parsed.toDate();
    }
  }
  let endDate: Date | null = null;
  if (stagedEndTime?.trim() && isValidIstWallClockString(stagedEndTime)) {
    // Parse IST time string to Date object
    // Format: "YYYY-MM-DD HH:mm:ss"
    const parsed = dayjs(stagedEndTime, "YYYY-MM-DD HH:mm:ss");
    if (parsed.isValid()) {
      endDate = parsed.toDate();
    }
  }

  const customMode = timePreset === HEATMAP_TIME_PRESET_CUSTOM;
  const fromError =
    customMode &&
    (!stagedStartTime?.trim() || !isValidIstWallClockString(stagedStartTime));
  const toError =
    customMode &&
    (!stagedEndTime?.trim() || !isValidIstWallClockString(stagedEndTime));

  const handleApply = () => {
    onChange({
      ...value,
      startTime: stagedStartTime,
      endTime: stagedEndTime,
    });
    onApply?.();
  };

  const handleReset = () => {
    setStagedStartTime(value.startTime);
    setStagedEndTime(value.endTime);
    const inferred = inferHeatmapTimePreset(value.startTime, value.endTime);
    setTimePreset(inferred);
    preferCustomRangeRef.current = false;
  };

  return (
    <Stack gap="sm">
      <Text size="xs" fw={700} c="dark">
        Date &amp; time (IST)
      </Text>
      <Select
        label="Quick range"
        size="xs"
        className={filterClasses.filterInput}
        data={selectData}
        value={timePreset}
        comboboxProps={{ withinPortal: false }}
        onChange={(v: string | null) => {
          const next = v ?? HEATMAP_TIME_PRESET_CUSTOM;
          if (next === HEATMAP_TIME_PRESET_CUSTOM) {
            preferCustomRangeRef.current = true;
          } else {
            preferCustomRangeRef.current = false;
          }
          setTimePreset(next);
          if (next !== HEATMAP_TIME_PRESET_CUSTOM) {
            const { startDate: st, endDate: et } = getStartAndEndDateTimeString(
              next,
              HEATMAP_TIME_RANGE_SUBTRACT_MINUTES,
            );
            setStagedStartTime(st);
            setStagedEndTime(et);
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
              onChange={(d: Date | null) => {
                preferCustomRangeRef.current = true;
                setTimePreset(HEATMAP_TIME_PRESET_CUSTOM);
                setStagedStartTime(getDateTimeStringFromDateValue(d));
              }}
              size="xs"
              clearable
              valueFormat="MMM D, YYYY HH:mm"
              maxDate={endDate ?? undefined}
              popoverProps={{ withinPortal: false }}
              error={fromError ? "Set a start date and time" : undefined}
            />
          </Stack>
          <Stack gap={4} style={{ minWidth: 180 }}>
            <Text size="xs" fw={600}>
              To
            </Text>
            <DateTimePicker
              value={endDate}
              onChange={(d: Date | null) => {
                preferCustomRangeRef.current = true;
                setTimePreset(HEATMAP_TIME_PRESET_CUSTOM);
                setStagedEndTime(getDateTimeStringFromDateValue(d));
              }}
              size="xs"
              clearable
              valueFormat="MMM D, YYYY HH:mm"
              minDate={startDate ?? undefined}
              popoverProps={{ withinPortal: false }}
              error={toError ? "Set an end date and time" : undefined}
            />
          </Stack>
        </Group>
      ) : null}
      <Divider />
      <Group justify="flex-end" gap="sm">
        <Button variant="outline" size="xs" onClick={handleReset}>
          Reset
        </Button>
        <Button
          variant="filled"
          size="xs"
          onClick={handleApply}
          disabled={customMode && (fromError || toError)}
        >
          Apply
        </Button>
      </Group>
    </Stack>
  );
}
