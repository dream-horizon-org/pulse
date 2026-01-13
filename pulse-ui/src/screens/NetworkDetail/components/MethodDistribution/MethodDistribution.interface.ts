export interface MethodDistributionProps {
  url: string;
  startTime: string;
  endTime: string;
  additionalFilters?: Array<{
    field: string;
    operator: "LIKE" | "EQ";
    value: string[];
  }>;
}

export interface MethodData {
  method: string;
  count: number;
  percentage: number;
  color: string;
}

