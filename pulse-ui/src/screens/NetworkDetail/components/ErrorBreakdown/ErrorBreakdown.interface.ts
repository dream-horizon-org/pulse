export interface ErrorDetail {
  statusCode: number;
  errorType: string;
  name: string;
  count: number;
  percentage: number;
  description: string;
}

export interface ErrorBreakdownProps {
  type: "4xx" | "5xx";
  url: string;
  startTime: string;
  endTime: string;
  additionalFilters?: Array<{
    field: string;
    operator: "LIKE" | "EQ";
    value: string[];
  }>;
}
