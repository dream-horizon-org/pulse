export type QueryRequestBody = { query: string };

export type RunUniversalQueryColumn = { name: string };
export type QueryRow = Record<string, string | number | boolean | null>;

export type QuerySuccessResponseBody = {
  schema: { fields: RunUniversalQueryColumn[] };
  rows: Array<{ f: Array<{ v: string }> }>;
  totalRows: string;
};

export type QuerySuccessResponse = {
  data: QuerySuccessResponseBody;
  status: number;
};

export type QueryErrorResponse = {
  data: null;
  error: { code: string; message: string; cause?: string };
  status: number;
};
