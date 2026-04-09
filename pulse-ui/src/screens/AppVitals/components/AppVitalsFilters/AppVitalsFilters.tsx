import classes from "../../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.module.css";
import {
  Autocomplete,
  Button,
  Popover,
  Stack,
  Badge,
  Group,
} from "@mantine/core";
import { IconFilter } from "@tabler/icons-react";
import { interactionDetailsfilterConstants } from "../../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.interface";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { filtersToQueryString } from "../../../../helpers/filtersToQueryString";
import { useFilterStore } from "../../../../stores/useFilterStore";
import type { CriticalInteractionDetailsFilterValues } from "../../../CriticalInteractionDetails/CriticalInteractionDetails.interface";
import { useGetDashboardFilters } from "../../../../hooks/useGetDashboardFilters";

const ALL_FILTERS: (keyof typeof interactionDetailsfilterConstants)[] = [
  "APP_VERSION",
  "PLATFORM",
  "OS_VERSION",
  "NETWORK_PROVIDER",
  "STATE",
];

const EMPTY_FILTERS: CriticalInteractionDetailsFilterValues = {
  PLATFORM: "",
  APP_VERSION: "",
  NETWORK_PROVIDER: "",
  STATE: "",
  OS_VERSION: "",
};

/**
 * Same popover + Autocomplete UX as Interaction Details filters. Option lists
 * come from `useGetDashboardFilters` (same API as Interaction Details).
 * Values sync to URL and `useFilterStore` (APP_VERSION, PLATFORM, …).
 */
export function AppVitalsFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [opened, setOpened] = useState(false);

  const {
    filterValues,
    timeFilterOptions,
    filterOptions,
    setFilterValues,
    handleFilterChange: storeHandleFilterChange,
    setFilterOptions,
    dateTimePickerOpened,
    toggleDateTimePickerOpened,
  } = useFilterStore();

  const { data: filterOptionsData } = useGetDashboardFilters();

  useEffect(() => {
    if (filterOptionsData?.data) {
      setFilterOptions({
        APP_VERSION: filterOptionsData.data.appVersionCodes || [],
        NETWORK_PROVIDER: filterOptionsData.data.networkProviders || [],
        OS_VERSION: filterOptionsData.data.osVersions || [],
        PLATFORM: filterOptionsData.data.platforms || [],
        STATE: filterOptionsData.data.states || [],
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterOptionsData]);

  useEffect(() => {
    setSearchParams(
      filtersToQueryString({
        ...Object.fromEntries(searchParams.entries()),
        ...(filterValues ?? EMPTY_FILTERS),
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterValues]);

  useEffect(() => {
    if (filterValues) {
      storeHandleFilterChange(
        filterValues,
        timeFilterOptions.startDate,
        timeFilterOptions.endDate,
      );
    }
  }, [
    filterValues,
    storeHandleFilterChange,
    timeFilterOptions.endDate,
    timeFilterOptions.startDate,
  ]);

  const handleFieldChange = (value: string, key: string) => {
    const current = filterValues ?? EMPTY_FILTERS;
    setFilterValues({
      ...current,
      [key]: value || "",
    });
  };

  const getActiveFilterCount = () => {
    if (!filterValues) return 0;
    return Object.values(filterValues).filter((value) => value !== "").length;
  };

  const activeCount = getActiveFilterCount();

  const handleFilterButtonClick = () => {
    setOpened((o) => !o);
    if (dateTimePickerOpened) {
      toggleDateTimePickerOpened();
    }
  };

  return (
    <Popover
      opened={opened}
      onChange={setOpened}
      width={400}
      position="bottom"
      withArrow
      shadow="md"
      closeOnEscape
      closeOnClickOutside
    >
      <Popover.Target>
        <Button
          variant="transparent"
          size="compact-sm"
          onClick={handleFilterButtonClick}
          className={classes.filterButton}
        >
          <Group gap={6} wrap="nowrap">
            <IconFilter size={14} stroke={2.5} />
            <span>Filters</span>
            {activeCount > 0 && (
              <Badge size="xs" circle className={classes.filterBadge}>
                {activeCount}
              </Badge>
            )}
          </Group>
        </Button>
      </Popover.Target>
      <Popover.Dropdown className={classes.filterDropdown}>
        <Stack gap="xs">
          {ALL_FILTERS.map((filter) => {
            const label =
              interactionDetailsfilterConstants[
                filter as keyof typeof interactionDetailsfilterConstants
              ];

            return (
              <Autocomplete
                key={filter}
                onOptionSubmit={(value) => handleFieldChange(value, filter)}
                data={
                  filterOptions[
                    filter as keyof typeof interactionDetailsfilterConstants
                  ] || []
                }
                onClear={() => handleFieldChange("", filter)}
                size="xs"
                label={label}
                className={classes.filterInput}
                placeholder="All"
                clearable
                value={
                  filterValues?.[
                    filter as keyof typeof interactionDetailsfilterConstants
                  ] || ""
                }
              />
            );
          })}
        </Stack>
      </Popover.Dropdown>
    </Popover>
  );
}
