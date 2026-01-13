export interface MethodTimeSeriesProps {
  url: string;
  startTime: string;
  endTime: string;
  additionalFilters?: Array<{
    field: string;
    operator: "LIKE" | "EQ";
    value: string[];
  }>;
}

