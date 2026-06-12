import { useEffect, useState } from "react";
import { Autocomplete, Box, Button } from "@mantine/core";
import { FilterProps } from "./Filters.interface";
import { FiltersType } from "../../ScreenList.interface";
import classes from "./Filters.module.css";
import { SCREEN_LISTING_PAGE_CONSTANTS } from "../../ScreenList.constants";
import { IDefaultFilterValues } from "./Filters.interface";

const defaultFilterValues: FiltersType = {
  users: "",
  statuses: "",
};

const defaultServerFilters: IDefaultFilterValues = {
  users: [],
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
      statuses: defaultFilters.statuses || "",
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
    const { users, statuses } = filters;
    return !!users || !!statuses;
  };

  return (
    <>
      <div className={classes.filterContainer}>
        {defaultFilterValuesFromServer ? (
          <>
            <Autocomplete
              comboboxProps={{ withinPortal: false }}
              className={classes.filterElement}
              data={defaultFilterValuesFromServer?.users || []}
              defaultValue={defaultFilters.users || ""}
              name="user"
              placeholder="Search by user"
              label="User"
              onChange={handleUserChange}
            />
          </>
        ) : (
          <div className={classes.loadingElemet}>Loading filters...</div>
        )}
      </div>
      <Box className={classes.filterButtonContainer}>
        <Button
          className={classes.filterButton}
          onClick={onReset}
          variant="outline"
          disabled={!filterHasValues()}
        >
          {SCREEN_LISTING_PAGE_CONSTANTS.CLEAR_ALL_FILTERS_TEXT}
        </Button>
        <Button
          className={classes.filterButton}
          onClick={onSubmit}
          disabled={!filterHasValues()}
        >
          {SCREEN_LISTING_PAGE_CONSTANTS.APPLY_FILTERS_TEXT}
        </Button>
      </Box>
    </>
  );
}

export default Filters;
