export interface MethodDistributionProps {
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

export interface MethodData {
  method: string;
  count: number;
  percentage: number;
  color: string;
}

