import type { ApiResponse } from "./makeRequest/makeRequest.interface";
import type {
  RcaJobResponse,
  RcaReportResponse,
} from "../hooks/useGetRcaReport/useGetRcaReport.interface";

function isRcaJobPayload(u: unknown): u is RcaJobResponse {
  return (
    u != null &&
    typeof u === "object" &&
    "jobId" in u &&
    typeof (u as RcaJobResponse).jobId === "string" &&
    "status" in u
  );
}

function isRcaReportPayload(u: unknown): u is RcaReportResponse {
  return (
    u != null &&
    typeof u === "object" &&
    ("report" in u || "cachedAt" in u || "cached" in u)
  );
}

/**
 * JAX-RS `Response.successfulResponse` uses `{ data: T, error }`. AI proxy may return bare `T`.
 */
export function unwrapRcaReportPostApiBody(
  res: ApiResponse<RcaReportResponse | RcaJobResponse>,
): ApiResponse<RcaReportResponse | RcaJobResponse> {
  const { data } = res;
  if (data == null || typeof data !== "object") {
    return res;
  }
  if (isRcaJobPayload(data) || isRcaReportPayload(data)) {
    return res;
  }
  const inner = (data as { data?: unknown }).data;
  if (inner != null && typeof inner === "object") {
    if (isRcaJobPayload(inner) || isRcaReportPayload(inner)) {
      return { ...res, data: inner as RcaReportResponse | RcaJobResponse };
    }
  }
  return res;
}

/**
 * Reads `jobId` from a POST /v1/ai/rca/report response (after optional `{ data }` unwrap).
 */
export function getJobIdFromRcaPostResponse(
  res: ApiResponse<RcaReportResponse | RcaJobResponse>,
): string | null {
  const { data } = unwrapRcaReportPostApiBody(res);
  if (data != null && isRcaJobPayload(data)) {
    const id = data.jobId.trim();
    return id !== "" ? id : null;
  }
  return null;
}

export function unwrapRcaJobApiBody(
  res: ApiResponse<RcaJobResponse>,
): ApiResponse<RcaJobResponse> {
  const { data } = res;
  if (data == null || typeof data !== "object") {
    return res;
  }
  if (isRcaJobPayload(data)) {
    return res;
  }
  const inner = (data as { data?: unknown }).data;
  if (inner != null && isRcaJobPayload(inner)) {
    return { ...res, data: inner };
  }
  return res;
}
