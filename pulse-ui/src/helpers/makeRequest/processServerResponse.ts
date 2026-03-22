import type {
  ApiResponse,
  DefaultErrorResponse,
} from "./makeRequest.interface";

const extractUnwrappedErrorMessage = (json: unknown): string | null => {
  if (json == null || typeof json !== "object") {
    return null;
  }
  const record = json as Record<string, unknown>;
  const detail = record.detail;
  if (typeof detail === "string" && detail.trim() !== "") {
    return detail;
  }
  if (Array.isArray(detail) && detail.length > 0) {
    const first = detail[0];
    if (first != null && typeof first === "object" && "msg" in first) {
      const msg = (first as { msg?: unknown }).msg;
      if (typeof msg === "string" && msg.trim() !== "") {
        return msg;
      }
    }
  }
  const message = record.message;
  if (typeof message === "string" && message.trim() !== "") {
    return message;
  }
  const error = record.error;
  if (typeof error === "string" && error.trim() !== "") {
    return error;
  }
  return null;
};

const buildHttpError = (
  status: number,
  json: unknown,
): DefaultErrorResponse => {
  const message =
    extractUnwrappedErrorMessage(json) ??
    `Request failed with status ${status}`;
  return {
    code: String(status),
    message,
    cause: "",
  };
};

export const processServerResponse = async <D>(
  response: Response,
  unwrapped?: boolean,
): Promise<ApiResponse<D>> => {
  const status = response.status;
  let json: unknown;
  try {
    const text = await response.text();
    json = text.trim() === "" ? null : JSON.parse(text);
  } catch {
    json = null;
  }

  if (unwrapped) {
    const isResponseOk = response.ok;
    if (!isResponseOk) {
      return {
        status,
        data: null,
        error: buildHttpError(status, json),
      };
    }
    return {
      status,
      data: json as D,
      error: null,
    };
  }

  const jsonInvalid = json == null || typeof json !== "object";
  if (jsonInvalid) {
    return {
      status,
      data: null,
      error: response.ok ? null : buildHttpError(status, json),
    };
  }

  const { data, error } = json as {
    data?: unknown;
    error?: DefaultErrorResponse | null;
  };

  if (data) {
    return {
      status: status,
      data: data as D,
      error: null,
    };
  }
  return {
    status: status,
    data: null,
    error: error ?? (!response.ok ? buildHttpError(status, json) : null),
  };
};
