import { useEffect, useMemo, useState } from "react";
import { Divider, Group, Stack, Text } from "@mantine/core";
import { useGetDashboardFilters } from "../../../hooks/useGetDashboardFilters";
import { useFilterStore } from "../../../stores/useFilterStore";
import { HeatmapAudienceFilterForm } from "./HeatmapAudienceFilterForm";
import { HeatmapAudienceFilterPills } from "./HeatmapAudienceFilterPills";
import { HeatmapAudienceFilterPopover } from "./HeatmapAudienceFilterPopover";
import { HeatmapMapViewControls } from "./HeatmapMapViewControls";
import { HeatmapTimeFilterPopover } from "./HeatmapTimeFilterPopover";
import { HeatmapTimeRangePopoverBody } from "./HeatmapTimeRangePopoverBody";
import type { HeatmapFilterPanelProps } from "./heatmap.ui.types";
import {
  countHeatmapAudienceFilters,
  formatHeatmapTimeButtonLabel,
} from "./heatmapFilterPanelUtils";

export type { HeatmapFilterPanelVariant, HeatmapFilterPanelProps } from "./heatmap.ui.types";

/**
 * Row 1 (full): map type / layer + optional `toolbarEnd` (e.g. Compare screens) on the right.
 * Row 2 (full): time + audience popovers + audience pills.
 * Compare column: section label, time + filters, then pills on the third row.
 */
export function HeatmapFilterPanel({
  variant = "full",
  value,
  onChange,
  onResetToPage,
  matchesPage = true,
  sectionLabel,
  dataOnlyLayout = "inline",
  toolbarEnd,
  signal = "tap",
  onSignalChange,
  focusLens = "all",
  onFocusLensChange,
  showInteractionMapOption = true,
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

  const timePopover = (
    <HeatmapTimeFilterPopover
      opened={timeOpen}
      onOpenChange={setTimeOpen}
      timeButtonLabel={timeButtonLabel}
      dropdownWidth={420}
    >
      {timePopoverBody}
    </HeatmapTimeFilterPopover>
  );

  const audiencePopover = (
    <HeatmapAudienceFilterPopover
      opened={filtersOpen}
      onOpenChange={setFiltersOpen}
      audienceActiveCount={audienceActiveCount}
      dropdownWidth={variant === "dataOnly" && dataOnlyLayout === "inline" ? 400 : 420}
      onResetToPage={onResetToPage}
      audienceHint={
        matchesPage
          ? variant === "dataOnly"
            ? "Using the same audience filters as the page header."
            : "Heatmap uses the same audience scope as this page unless you change something here."
          : variant === "dataOnly"
            ? "Custom audience filters for this side of the comparison."
            : "Custom audience filters for this heatmap only."
      }
    >
      {audienceForm}
    </HeatmapAudienceFilterPopover>
  );

  if (variant === "dataOnly") {
    if (dataOnlyLayout === "compareColumn") {
      return (
        <Stack gap="xs">
          {sectionLabel ? (
            <Text size="sm" fw={600}>
              {sectionLabel}
            </Text>
          ) : null}
          <Group gap="sm" wrap="wrap">
            {timePopover}
            {audiencePopover}
          </Group>
          <HeatmapAudienceFilterPills value={value} onChange={onChange} />
        </Stack>
      );
    }

    return (
      <Stack gap={6}>
        {sectionLabel ? (
          <Text size="sm" fw={600}>
            {sectionLabel}
          </Text>
        ) : null}
        <Group gap="sm" wrap="wrap">
          {timePopover}
          {audiencePopover}
        </Group>
      </Stack>
    );
  }

  return (
    <Stack gap="sm" style={{ flex: 1, minWidth: 0, width: "100%" }}>
      <Group justify="space-between" align="center" wrap="wrap" w="100%" gap="md">
        <HeatmapMapViewControls
          signal={signal}
          onSignalChange={onSignalChange}
          focusLens={focusLens}
          onFocusLensChange={onFocusLensChange}
          showInteractionMapOption={showInteractionMapOption}
        />
        {toolbarEnd}
      </Group>
      <Divider
        w="100%"
        color="var(--mantine-color-gray-3)"
        style={{ opacity: 0.85 }}
      />
      <Group gap="sm" wrap="wrap" align="center">
        {timePopover}
        {audiencePopover}
        <HeatmapAudienceFilterPills value={value} onChange={onChange} />
      </Group>
    </Stack>
  );
}
