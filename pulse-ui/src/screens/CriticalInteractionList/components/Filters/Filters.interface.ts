import { FiltersType } from "../../CriticalInteractionList.interface";
import { GetInteractionListFiltersResponse } from "../../../../hooks/useGetInteractionListFilters/useGetInteractionListFilters.interface";

export type FilterProps = {
  defaultFilters: FiltersType;
  defaultFilterValuesFromServer: GetInteractionListFiltersResponse | null | undefined;
  handleFiltersChange: (filter: FiltersType) => void;
};
