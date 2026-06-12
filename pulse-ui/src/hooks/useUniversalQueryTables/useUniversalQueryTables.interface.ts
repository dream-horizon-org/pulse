import { ApiResponse } from "../../helpers/makeRequest";

export type Table = {
  name: string;
};
export type UniversalQueryTablesSuccessResponseBody = Table[];
export type UniversalQueryTablesSuccessResponse = ApiResponse<Table[]>;
export type UniversalQueryTablesErrorResponse = {
  error: {
    message: string;
    cause: string;
  };
  data: null;
  status: number;
};
