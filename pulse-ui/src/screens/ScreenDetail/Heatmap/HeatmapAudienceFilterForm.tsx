import { Autocomplete, Select, Stack, Text } from "@mantine/core";
import type { OptionsFilter } from "@mantine/core";
import type { HeatmapAudienceFilterFormProps } from "./heatmap.ui.types";
import filterClasses from "../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.module.css";
import {
  HEATMAP_BREAKPOINT_NAMES,
  HEATMAP_BREAKPOINT_VALUES,
} from "./heatmap.types";
import { canonicalHeatmapBreakpoint } from "./heatmapLocalFilters";

/** Show full suggestion lists; default Autocomplete filters by input and hides most rows when a value is set. */
const heatmapAudienceOptionsFilter: OptionsFilter = ({ options }) => options;

export function HeatmapAudienceFilterForm({
  value,
  onChange,
  platformSuggestions,
  appVersionSuggestions,
  regionSuggestions,
}: HeatmapAudienceFilterFormProps) {
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
        value={value.platform}
        comboboxProps={{ withinPortal: false }}
        filter={heatmapAudienceOptionsFilter}
        onChange={(v) => onChange({ ...value, platform: v ?? "" })}
        placeholder="All"
      />
      <Autocomplete
        label="App version"
        size="xs"
        className={filterClasses.filterInput}
        data={appVersionSuggestions}
        value={value.appVersion}
        comboboxProps={{ withinPortal: false }}
        filter={heatmapAudienceOptionsFilter}
        onChange={(v) => onChange({ ...value, appVersion: v ?? "" })}
        placeholder="All"
      />
      <Autocomplete
        label="Region"
        size="xs"
        className={filterClasses.filterInput}
        data={regionSuggestions}
        value={value.region}
        comboboxProps={{ withinPortal: false }}
        filter={heatmapAudienceOptionsFilter}
        onChange={(v) => onChange({ ...value, region: v ?? "" })}
        placeholder="All"
      />
      <Select
        label="Viewport"
        size="xs"
        className={filterClasses.filterInput}
        clearable
        placeholder="All"
        comboboxProps={{ withinPortal: false }}
        data={HEATMAP_BREAKPOINT_VALUES.map((v) => ({
          value: v,
          label: HEATMAP_BREAKPOINT_NAMES[v],
        }))}
        value={canonicalHeatmapBreakpoint(value.breakpoint) || null}
        onChange={(v) => onChange({ ...value, breakpoint: v ?? "" })}
      />
    </Stack>
  );
}
