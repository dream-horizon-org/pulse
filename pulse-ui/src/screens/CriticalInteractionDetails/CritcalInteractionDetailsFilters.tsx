import { Group } from "@mantine/core";
import classes from "./CriticalInteractionDetails.module.css";
import { CriticalInteractionDetailsFilterValues } from "./CriticalInteractionDetails.interface";
import { CriticalInteractionDetailsFilterOptionsResponse } from "../../helpers/getCriticalInteractionDetailsFilterOptions";

export type CriticalInteractionDetailsFiltersProps = {
  disableAddFilterButton: boolean;
  showLoader: boolean;
  filterValues: CriticalInteractionDetailsFilterValues;
  filterOptions: CriticalInteractionDetailsFilterOptionsResponse;
  onFilterChange: (key: string, value: string) => void;
  onApplyFiltersButtonClick: () => void;
};

export function CriticalInteractionDetailsFilters({
  disableAddFilterButton,
  showLoader,
  filterValues,
  filterOptions,
  onFilterChange,
  onApplyFiltersButtonClick,
}: CriticalInteractionDetailsFiltersProps) {
  return (
    <Group className={classes.allInteractionDetailsFilters}>
      {/* <Autocomplete
        label={CRITICAL_INTERACTION_DETAILS_FILTER_LABELS.PLATFORM}
        value={filterValues.PLATFORM}
        onChange={(value: string) =>
          onFilterChange(
            CRITICAL_INTERACTION_DETAILS_FILTER_KEYS.PLATFORM,
            value,
          )
        }
        data={filterOptions.platforms}
      />
      <Autocomplete
        label={CRITICAL_INTERACTION_DETAILS_FILTER_LABELS.APP_VERSION}
        value={filterValues.APP_VERSION}
        onChange={(value: string) =>
          onFilterChange(
            CRITICAL_INTERACTION_DETAILS_FILTER_KEYS.APP_VERSION,
            value,
          )
        }
        data={filterOptions.appVersions}
      />
      <Autocomplete
        label={CRITICAL_INTERACTION_DETAILS_FILTER_LABELS.NETWORK_PROVIDER}
        value={filterValues.NETWORK_PROVIDER}
        onChange={(value: string) =>
          onFilterChange(
            CRITICAL_INTERACTION_DETAILS_FILTER_KEYS.NETWORK_PROVIDER,
            value,
          )
        }
        data={filterOptions.networkProviders}
      />
      <Autocomplete
        label={CRITICAL_INTERACTION_DETAILS_FILTER_LABELS.PERCENTILE}
        value={filterValues.PERCENTILE}
        onChange={(value: string) =>
          onFilterChange(
            CRITICAL_INTERACTION_DETAILS_FILTER_KEYS.PERCENTILE,
            value,
          )
        }
        data={filterOptions.percentiles}
      />
      <Autocomplete
        label={CRITICAL_INTERACTION_DETAILS_FILTER_LABELS.STATE}
        value={filterValues.STATE}
        onChange={(value: string) =>
          onFilterChange(CRITICAL_INTERACTION_DETAILS_FILTER_KEYS.STATE, value)
        }
        data={filterOptions.states}
      />
      <Autocomplete
        label={CRITICAL_INTERACTION_DETAILS_FILTER_LABELS.OS_VERSION}
        value={filterValues.OS_VERSION}
        onChange={(value: string) =>
          onFilterChange(
            CRITICAL_INTERACTION_DETAILS_FILTER_KEYS.OS_VERSION,
            value,
          )
        }
        data={filterOptions.osVersions}
      />
      <Autocomplete
        label={CRITICAL_INTERACTION_DETAILS_FILTER_LABELS.DEVICE_MODEL}
        value={filterValues.DEVICE_MODEL}
        onChange={(value: string) =>
          onFilterChange(
            CRITICAL_INTERACTION_DETAILS_FILTER_KEYS.DEVICE_MODEL,
            value,
          )
        }
        data={filterOptions.deviceModels}
      />
      <Select
        label={
          CRITICAL_INTERACTION_DETAILS_PAGE_CONSTANTS.TIME_RANGE_LABEL_TEXT
        }
        data={CRITICAL_INTERACTION_DETAILS_TIME_FILTERS}
        // Show 30 minutes data as default
        defaultValue={CRITICAL_INTERACTION_DETAILS_TIME_FILTERS[2].value}
        value={filterValues.TIME_RANGE ?? null}
        searchable={false}
        onChange={(value) =>
          onFilterChange(
            CRITICAL_INTERACTION_DETAILS_FILTER_KEYS.TIME_RANGE,
            value ?? "",
          )
        }
      />
      <Button
        className={classes.applyFiltersButton}
        loading={showLoader}
        disabled={disableAddFilterButton}
        onClick={onApplyFiltersButtonClick}
        loaderProps={{ type: "bars" }}
      >
        {CRITICAL_INTERACTION_DETAILS_PAGE_CONSTANTS.APPLY_FILTERS_BUTTON_TEXT}
      </Button> */}
    </Group>
  );
}
