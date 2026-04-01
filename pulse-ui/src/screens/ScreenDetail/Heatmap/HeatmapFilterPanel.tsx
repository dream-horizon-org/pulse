import { useEffect, useMemo, useState } from "react";
import { Divider, Group, Stack, Text } from "@mantine/core";
import { useGetDashboardFilters } from "../../../hooks/useGetDashboardFilters";
import { useFilterStore } from "../../../stores/useFilterStore";
import { HeatmapAudienceFilterForm } from "./HeatmapAudienceFilterForm";
import { HeatmapAudienceFilterPopover } from "./HeatmapAudienceFilterPopover";
import { HeatmapMapViewControls } from "./HeatmapMapViewControls";
import { HeatmapTimeFilterPopover } from "./HeatmapTimeFilterPopover";
import { HeatmapTimeRangePopoverBody } from "./HeatmapTimeRangePopoverBody";
import type { HeatmapFilterPanelProps } from "./heatmapFilterPanel.types";
import {
  countHeatmapAudienceFilters,
  formatHeatmapTimeButtonLabel,
} from "./heatmapFilterPanelUtils";

export type { HeatmapFilterPanelVariant, HeatmapFilterPanelProps } from "./heatmapFilterPanel.types";

/**
 * Time and audience use the same popover pattern as the page header.
 * Map type (heat vs interaction) and heat-map layer (Tap / Rage / Dead) stay
 * on the toolbar so they stay visible while adjusting filters.
 */
export function HeatmapFilterPanel({
  variant = "full",
  value,
  onChange,
  onResetToPage,
  matchesPage = true,
  timeMatchesPage = true,
  sectionLabel,
  signal = "tap",
  onSignalChange,
  focusLens = "all",
  onFocusLensChange,
}: HeatmapFilterPanelProps) {
  const { setFilterOptions } = useFilterStore();
  const { data: filterOptionsData } = useGetDashboardFilters();
  const [timeOpen, setTimeOpen] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);

  useEffect(() => {
    if (filterOptionsData?.data) {
      setFilterOptions({
        APP_VERSION: filterOptionsData.data.appVersionCodes ?? [],
        NETWORK_PROVIDER: filterOptionsData.data.networkProviders ?? [],
        OS_VERSION: filterOptionsData.data.osVersions ?? [],
        PLATFORM: filterOptionsData.data.platforms ?? [],
        STATE: filterOptionsData.data.states ?? [],
      });
    }
  }, [filterOptionsData, setFilterOptions]);

  const platformSuggestions = useMemo(
    () => filterOptionsData?.data?.platforms ?? [],
    [filterOptionsData],
  );
  const appVersionSuggestions = useMemo(
    () => filterOptionsData?.data?.appVersionCodes ?? [],
    [filterOptionsData],
  );
  const regionSuggestions = useMemo(
    () => filterOptionsData?.data?.states ?? [],
    [filterOptionsData],
  );

  if (variant === "mapOnly") {
    return (
      <HeatmapMapViewControls
        signal={signal}
        onSignalChange={onSignalChange}
        focusLens={focusLens}
        onFocusLensChange={onFocusLensChange}
      />
    );
  }

  if (!value || !onChange) {
    return null;
  }

  const timeButtonLabel = formatHeatmapTimeButtonLabel(value);
  const audienceActiveCount = countHeatmapAudienceFilters(value);

  const audienceForm = (
    <HeatmapAudienceFilterForm
      value={value}
      onChange={onChange}
      platformSuggestions={platformSuggestions}
      appVersionSuggestions={appVersionSuggestions}
      regionSuggestions={regionSuggestions}
    />
  );

  const timePopoverBody = (
    <HeatmapTimeRangePopoverBody
      opened={timeOpen}
      value={value}
      onChange={onChange}
    />
  );

  if (variant === "dataOnly") {
    return (
      <Stack gap={6}>
        {sectionLabel ? (
          <Text size="sm" fw={600}>
            {sectionLabel}
          </Text>
        ) : null}
        <Group gap="sm" wrap="wrap">
          <HeatmapTimeFilterPopover
            opened={timeOpen}
            onOpenChange={setTimeOpen}
            timeButtonLabel={timeButtonLabel}
            timeMatchesPage={timeMatchesPage}
            dropdownWidth={420}
          >
            {timePopoverBody}
          </HeatmapTimeFilterPopover>

          <HeatmapAudienceFilterPopover
            opened={filtersOpen}
            onOpenChange={setFiltersOpen}
            audienceActiveCount={audienceActiveCount}
            dropdownWidth={400}
            onResetToPage={onResetToPage}
            audienceHint={
              matchesPage
                ? "Using the same audience filters as the page header."
                : "Custom audience filters for this side of the comparison."
            }
          >
            {audienceForm}
          </HeatmapAudienceFilterPopover>
        </Group>
      </Stack>
    );
  }

  return (
    <Group
      gap="md"
      wrap="wrap"
      align="center"
      style={{ flex: "0 1 auto", minWidth: 0 }}
    >
      <Group gap="sm" wrap="wrap">
        <HeatmapTimeFilterPopover
          opened={timeOpen}
          onOpenChange={setTimeOpen}
          timeButtonLabel={timeButtonLabel}
          timeMatchesPage={timeMatchesPage}
        >
          {timePopoverBody}
        </HeatmapTimeFilterPopover>

        <HeatmapAudienceFilterPopover
          opened={filtersOpen}
          onOpenChange={setFiltersOpen}
          audienceActiveCount={audienceActiveCount}
          onResetToPage={onResetToPage}
          audienceHint={
            matchesPage
              ? "Heatmap uses the same audience scope as this page unless you change something here."
              : "Custom audience filters for this heatmap only."
          }
        >
          {audienceForm}
        </HeatmapAudienceFilterPopover>
      </Group>

      <Divider orientation="vertical" h={28} />

      <HeatmapMapViewControls
        signal={signal}
        onSignalChange={onSignalChange}
        focusLens={focusLens}
        onFocusLensChange={onFocusLensChange}
      />
    </Group>
  );
}
