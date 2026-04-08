import { Autocomplete, Stack, Text } from "@mantine/core";
import type { HeatmapAudienceFilterFormProps } from "./heatmapFilterPanel.types";
import filterClasses from "../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.module.css";

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
        onChange={(v) => onChange({ ...value, platform: v ?? "" })}
        placeholder="All"
      />
      <Autocomplete
        label="App version"
        size="xs"
        className={filterClasses.filterInput}
        data={appVersionSuggestions}
        value={value.appVersion}
        onChange={(v) => onChange({ ...value, appVersion: v ?? "" })}
        placeholder="All"
      />
      <Autocomplete
        label="Region"
        size="xs"
        className={filterClasses.filterInput}
        data={regionSuggestions}
        value={value.region}
        onChange={(v) => onChange({ ...value, region: v ?? "" })}
        placeholder="All"
      />
    </Stack>
  );
}
