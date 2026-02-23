export type FilterType =
  | "AppVersion"
  | "DeviceModel"
  | "Platform"
  | "GeoState"
  | "OsVersion"
  | "ScreenName"
  | "InteractionName"
  | "HttpStatusCode"
  | "ReqHeader"
  | "ResHeader";

export interface AppliedFilter {
  type: FilterType;
  value: string;
  key?: string;
  operator?: "LIKE" | "EQ";
  id: string;
}

export interface NetworkFiltersProps {
  appliedFilters: AppliedFilter[];
  onAddFilter: (
    type: FilterType,
    value: string,
    options?: { key?: string; operator?: "LIKE" | "EQ" }
  ) => void;
  onRemoveFilter: (id: string) => void;
}
