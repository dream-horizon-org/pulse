import { FiltersType } from "../../AlertListingPage.interface";

export type AlertFiltersProps = {
  options: {
    created_by: string[];
    scope: string[];
    updated_by: string[];
  };
  onFilterSave: (filters: FiltersType) => void;
  onFilterReset: () => void;
  created_by: string | null;
  scope: string | null;
  updated_by: string | null;
};


