export interface MethodTimeSeriesProps {
  url: string;
  startTime: string;
  endTime: string;
  additionalFilters?: Array<{
    field: string;
    operator: "LIKE" | "EQ";
    value: string[];
  }>;
  mode?: "http_method" | "graphql_operation_type";
  queryResult?: {
    data?: any;
    isLoading?: boolean;
    error?: unknown;
  };
}

