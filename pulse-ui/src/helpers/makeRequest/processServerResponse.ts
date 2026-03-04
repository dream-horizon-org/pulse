import { ApiResponse } from "./makeRequest.interface";

export const processServerResponse = async <D>(
  response: Response,
  unwrapped?: boolean,
): Promise<ApiResponse<D>> => {
  const status = response.status;
  const json = await response.json();

  if (unwrapped) {
    // Endpoint returns raw data directly (no { data, error } wrapper)
    return {
      status,
      data: json as D,
      error: null,
    };
  }

  const { data, error } = json;

  if (data) {
    return {
      status: status,
      data: data,
      error: null,
    };
  } else {
    return {
      status: status,
      data: null,
      error: error,
    };
  }
};
