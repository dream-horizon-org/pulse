import { FiltersType } from "../../ScreenList.interface";
export interface IDefaultFilterValues {
  users: string[];
  statuses: string[];
}

export interface FilterProps {
  defaultFilters?: FiltersType;
  handleFiltersChange: (updatedValues: FiltersType) => void;
  defaultFilterValuesFromServer?: IDefaultFilterValues;
}
