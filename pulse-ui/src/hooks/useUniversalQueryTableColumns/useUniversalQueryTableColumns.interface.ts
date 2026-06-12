export type TableColumn = {
  name: string;
  type: string;
};

export type UniversalQueryTableColumnsSuccessResponseBody = TableColumn[];
export type UniversalQueryTableColumnsSuccessResponse = {
  data: UniversalQueryTableColumnsSuccessResponseBody;
  status: number;
};
export type UniversalQueryTableColumnsErrorResponse = {
  error: {
    message: string;
    cause: string;
  };
  data: null;
  status: number;
};
