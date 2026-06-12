export type FunctionType =
  | "APDEX"
  | "CRASH"
  | "ANR"
  | "FROZEN_FRAME"
  | "ANALYSED_FRAME"
  | "UNANALYSED_FRAME"
  | "DURATION_P99"
  | "DURATION_P50"
  | "DURATION_P95"
  | "COL"
  | "CUSTOM"
  | "TIME_BUCKET"
  | "INTERACTION_SUCCESS_COUNT"
  | "INTERACTION_ERROR_COUNT"
  | "INTERACTION_ERROR_DISTINCT_USERS"
  | "USER_CATEGORY_EXCELLENT"
  | "USER_CATEGORY_GOOD"
  | "USER_CATEGORY_AVERAGE"
  | "USER_CATEGORY_POOR"
  | "NET_0"
  | "NET_2XX"
  | "NET_3XX"
  | "NET_4XX"
  | "NET_5XX";

export type DataType = "TRACES" | "EVENTS" | "METRICS" | "LOGS" | "EXCEPTIONS";

export type OperatorType =
  | "EQ"
  | "IN"
  | "NE"
  | "GT"
  | "LT"
  | "GTE"
  | "LTE"
  | "LIKE"
  | "ADDITIONAL";

export interface SelectField {
  function: FunctionType;
  alias: string;
  param?: {
    field?: string;
    [key: string]: any;
  };
}

export interface FilterField {
  field: string;
  operator: OperatorType;
  value: string | string[] | number | number[] | boolean | boolean[];
}

export interface TimeRange {
  start: string;
  end: string;
}

export interface DataQueryRequestBody {
  dataType: DataType;
  timeRange: TimeRange;
  select: SelectField[];
  groupBy?: string[];
  filters?: FilterField[];
  orderBy?: {
    field: string;
    direction: "ASC" | "DESC";
  }[];
  limit?: number;
  offset?: number;
}

export interface DataQueryResponse {
  fields: string[];
  rows: string[][];
}

export interface GetDataQueryParams {
  requestBody: DataQueryRequestBody;
  enabled?: boolean;
  refetchInterval?: number | false;
}
