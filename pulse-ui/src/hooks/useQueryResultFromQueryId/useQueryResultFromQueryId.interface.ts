import { ApiResponse } from "../../helpers/makeRequest";

export type RunUniversalQueryRequestBody = {
  requestId: string;
  pageToken: string;
};

export type RunUniversalQueryColumn = { name: string };
export type RunUniversalQueryRow = { f: Array<{ v: string | any[] }> };

export type RunUniversalQuerySuccessResponseBody = {
  schema: { fields: RunUniversalQueryColumn[] };
  rows: RunUniversalQueryRow[];
  totalRows: number;
  pageToken?: string;
  jobComplete?: boolean;
};

export type RunUniversalQuerySuccessResponse =
  ApiResponse<RunUniversalQuerySuccessResponseBody>;

export type RunUniversalQueryErrorResponse = {
  data: null;
  error: { code: string; message: string; cause?: string };
  status: number;
  jobComplete: boolean;
  totalRows: number;
};
