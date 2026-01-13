export interface StatusCodeTimeSeriesProps {
  url: string;
  startTime: string;
  endTime: string;
  additionalFilters?: Array<{
    field: string;
    operator: "LIKE" | "EQ";
    value: string[];
  }>;
}

export interface TimeSeriesDataPoint {
  timestamp: string;
  "2xx": number;
  "3xx": number;
  "4xx": number;
  "5xx": number;
  "0xx": number;
}

