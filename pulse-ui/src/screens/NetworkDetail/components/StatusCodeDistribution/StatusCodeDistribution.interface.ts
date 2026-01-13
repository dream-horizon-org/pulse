export interface StatusCodeDistributionProps {
  url: string;
  startTime: string;
  endTime: string;
  additionalFilters?: Array<{
    field: string;
    operator: "LIKE" | "EQ";
    value: string[];
  }>;
}

export interface StatusCodeData {
  statusCode: string;
  count: number;
  percentage: number;
  color: string;
}

export interface StatusCodeCategory {
  category: string;
  label: string;
  count: number;
  percentage: number;
  color: string;
  statusCodes: StatusCodeData[];
}

