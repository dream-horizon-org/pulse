import { useEffect, useState } from "react";
import { Autocomplete, Box, Button, Select } from "@mantine/core";
import { FilterProps } from "./Filters.interface";
import { FiltersType } from "../../CriticalInteractionList.interface";
import classes from "./Filters.module.css";
import { CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS, RADIO_LABLES } from "../../../../constants";
import { GetInteractionListFiltersResponse } from "../../../../hooks/useGetInteractionListFilters/useGetInteractionListFilters.interface";

const defaultFilterValues: FiltersType = {
  users: "",
  status: "",
};

const defaultServerFilters: GetInteractionListFiltersResponse = {
  createdBy: [],
  statuses: [],
};

export function Filters({
  defaultFilters = defaultFilterValues,
  handleFiltersChange,
  defaultFilterValuesFromServer = defaultServerFilters,
}: FilterProps) {
  const [filters, setFilters] = useState<FiltersType>(defaultFilterValues);
  useEffect(() => {
    setFilters({
      users: defaultFilters.users || "",
      status: defaultFilters.status || "",
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onSubmit = (event: any) => {
    event.preventDefault();

    handleFiltersChange({
      ...filters,
    } as FiltersType);
  };

  const handleUserChange = (value: string) => {
    setFilters({
      ...filters,
      users: value,
    } as FiltersType);
  };

  const onReset = () => {
    setFilters(defaultFilterValues);
    handleFiltersChange(defaultFilterValues);
  };

  const filterHasValues = () => {
    const { users, status: statuses } = filters;
    return !!users || !!statuses;
  };

  const handleStatusChange = (value: string | null) => {
    if (!value) {
      return;
    }

    setFilters({
      ...filters,
      status: value,
    } as FiltersType);
  };

  return (
    <>
      <div className={classes.filterContainer}>
        {defaultFilterValuesFromServer ? (
          <>
            <Autocomplete
              comboboxProps={{ withinPortal: false }}
              className={classes.filterElement}
              data={defaultFilterValuesFromServer?.createdBy || []}
              defaultValue={defaultFilters.users || ""}
              name="user"
              placeholder={
                CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS.FILTER_SEARCH_BAR_PLACEHOLDER_TEXT
              }
              label={
                CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS.FILTER_SEARCH_BAR_LABEL_TEXT
              }
              onChange={handleUserChange}
            />
            <Select
              className={classes.filterElement}
              data={defaultFilterValuesFromServer?.statuses || []}
              defaultValue={defaultFilters.status || ""}
              label={RADIO_LABLES.STATUS}
              onChange={handleStatusChange}
              placeholder="Select interaction id"
              comboboxProps={{ withinPortal: false }}
            />
          </>
        ) : (
          <div className={classes.loadingElemet}>
            {CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS.LOADING_TEXT}
          </div>
        )}
      </div>
      <Box className={classes.filterButtonContainer}>
        <Button
          className={classes.filterButton}
          onClick={onReset}
          variant="outline"
          disabled={!filterHasValues()}
        >
          {CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS.RESET_BUTTON_TEXT}
        </Button>
        <Button
          className={classes.filterButton}
          onClick={onSubmit}
          disabled={!filterHasValues()}
        >
          {CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS.APPLY_BUTTON_TEXT}
        </Button>
      </Box>
    </>
  );
}

export default Filters;
