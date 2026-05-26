import { Autocomplete, Select, Stack, Text } from "@mantine/core";
import type { OptionsFilter } from "@mantine/core";
import { useState, useEffect, useImperativeHandle, forwardRef } from "react";
import type { HeatmapAudienceFilterFormProps } from "./heatmap.ui.types";
import filterClasses from "../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.module.css";
import {
  HEATMAP_BREAKPOINT_NAMES,
  HEATMAP_BREAKPOINT_VALUES,
} from "./heatmap.types";
import { canonicalHeatmapBreakpoint } from "./heatmapLocalFilters";

/** Show full suggestion lists; default Autocomplete filters by input and hides most rows when a value is set. */
const heatmapAudienceOptionsFilter: OptionsFilter = ({ options }: any) => options;

export interface HeatmapAudienceFilterFormHandle {
  apply: () => void;
  reset: () => void;
}

export const HeatmapAudienceFilterForm = forwardRef<
  HeatmapAudienceFilterFormHandle,
  HeatmapAudienceFilterFormProps
>(
  (
    {
      value,
      onChange,
      platformSuggestions,
      appVersionSuggestions,
      regionSuggestions,
    }: HeatmapAudienceFilterFormProps,
    ref: any,
  ) => {
    const [stagedValue, setStagedValue] = useState<typeof value>(value);

    useEffect(() => {
      setStagedValue(value);
    }, [value]);

    const handleChange = (updates: Partial<typeof value>) => {
      setStagedValue((prev: typeof value) => ({ ...prev, ...updates }));
    };

    // Define empty/default filter values
    const getEmptyFilters = () => ({
      platform: "",
      appVersion: "",
      region: "",
      breakpoint: "",
    });

    useImperativeHandle(ref, () => ({
      apply: () => {
        onChange(stagedValue);
      },
      reset: () => {
        // Reset to empty filters, not to current value
        const emptyFilters = getEmptyFilters();
        setStagedValue((prev: typeof value) => ({
          ...prev,
          ...emptyFilters,
        }));
      },
    }));

    return (
      <Stack gap="xs">
        <Text size="xs" fw={700} c="dark">
          Audience
        </Text>
        <Autocomplete
          label="Platform"
          size="xs"
          className={filterClasses.filterInput}
          data={platformSuggestions}
          value={stagedValue.platform}
          comboboxProps={{ withinPortal: false }}
          filter={heatmapAudienceOptionsFilter}
          onChange={(v: string) => handleChange({ platform: v ?? "" })}
          placeholder="All"
        />
        <Autocomplete
          label="App version"
          size="xs"
          className={filterClasses.filterInput}
          data={appVersionSuggestions}
          value={stagedValue.appVersion}
          comboboxProps={{ withinPortal: false }}
          filter={heatmapAudienceOptionsFilter}
          onChange={(v: string) => handleChange({ appVersion: v ?? "" })}
          placeholder="All"
        />
        <Autocomplete
          label="Region"
          size="xs"
          className={filterClasses.filterInput}
          data={regionSuggestions}
          value={stagedValue.region}
          comboboxProps={{ withinPortal: false }}
          filter={heatmapAudienceOptionsFilter}
          onChange={(v: string) => handleChange({ region: v ?? "" })}
          placeholder="All"
        />
        <Select
          label="Viewport"
          size="xs"
          className={filterClasses.filterInput}
          clearable
          placeholder="All"
          comboboxProps={{ withinPortal: false }}
          data={HEATMAP_BREAKPOINT_VALUES.map((v: string) => ({
            value: v,
            label: HEATMAP_BREAKPOINT_NAMES[v as keyof typeof HEATMAP_BREAKPOINT_NAMES],
          }))}
          value={canonicalHeatmapBreakpoint(stagedValue.breakpoint) || null}
          onChange={(v: string | null) => handleChange({ breakpoint: v ?? "" })}
        />
      </Stack>
    );
  },
);

HeatmapAudienceFilterForm.displayName = "HeatmapAudienceFilterForm";
