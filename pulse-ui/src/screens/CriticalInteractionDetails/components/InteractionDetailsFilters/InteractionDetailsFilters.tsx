import classes from "./InteractionDetailsFilters.module.css";
import {
  Autocomplete,
  Button,
  Popover,
  Stack,
  Badge,
  Group,
} from "@mantine/core";
import { IconFilter } from "@tabler/icons-react";
import { interactionDetailsfilterConstants } from "./InteractionDetailsFilters.interface";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { filtersToQueryString } from "../../../../helpers/filtersToQueryString";
import { useFilterStore } from "../../../../stores/useFilterStore";
import { useGetDashboardFilters } from "../../../../hooks/useGetDashboardFilters";

const ALL_FILTERS: (keyof typeof interactionDetailsfilterConstants)[] = [
  "APP_VERSION",
  "PLATFORM",
  "OS_VERSION",
  "NETWORK_PROVIDER",
  "STATE",
];

export function InteractionDetailsFilters() {
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

  const { data: filterOptionsData} = useGetDashboardFilters();

  useEffect(() => {
    if (filterOptionsData?.data) {
      setFilterOptions({
        APP_VERSION: filterOptionsData?.data?.appVersionCodes || [],
        NETWORK_PROVIDER: filterOptionsData?.data?.networkProviders || [],
        OS_VERSION: filterOptionsData?.data?.osVersions || [],
        PLATFORM: filterOptionsData?.data?.platforms || [],
        STATE: filterOptionsData?.data?.states || [],
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterOptionsData]);

  useEffect(() => {
    setSearchParams(
      filtersToQueryString({
        ...Object.fromEntries(searchParams.entries()),
        ...filterValues,
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

  const handleFilterChange = (value: string, key: string) => {
    if (filterValues) {
      setFilterValues({
        ...filterValues,
        [key]: value || "",
      });
    }
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
  }

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
                onOptionSubmit={(value) => handleFilterChange(value, filter)}
                data={
                  filterOptions[
                    filter as keyof typeof interactionDetailsfilterConstants
                  ] || []
                }
                onClear={() => handleFilterChange("", filter)}
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
