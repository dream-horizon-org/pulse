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

  // Prefer an explicit API `error` payload even when HTTP status is 2xx (e.g. misbehaving proxies)
  // or when both `data` and `error` appear (defensive: surface the error).
  if (error != null) {
    return {
      status,
      data: data ?? null,
      error,
    };
  }

  if (data !== undefined && data !== null) {
    return {
      status,
      data,
      error: null,
    };
  }

  return {
    status,
    data: null,
    error: null,
  };
};
