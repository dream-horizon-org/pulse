export type FilterType =
  | "AppVersion"
  | "DeviceModel"
  | "Platform"
  | "GeoState"
  | "OsVersion"
  | "ScreenName"
  | "InteractionName";

export interface AppliedFilter {
  type: FilterType;
  value: string;
  id: string;
}

export interface NetworkFiltersProps {
  appliedFilters: AppliedFilter[];
  onAddFilter: (type: FilterType, value: string) => void;
  onRemoveFilter: (id: string) => void;
}
