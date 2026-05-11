import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import type { ApiResponse } from "../../helpers/makeRequest";
import { GET_SCREEN_ROOT_CAUSE_ROUTE } from "../../constants/API";
import type {
  ScreenRootCauseData,
  UseGetScreenRootCauseParams,
} from "./useGetScreenRootCause.interface";
import { getMockScreenRootCauseApiResponse } from "../../mocks/mockScreenRcaReport";

function buildScreenRootCauseUrl(
  screenName: string,
  date: string | undefined,
  asOfIso: string | undefined,
  options?: { forceRefresh?: boolean },
): string {
  const base = getApiBaseUrl().replace(/\/$/, "");
  const encoded = encodeURIComponent(screenName);
  const path = `${GET_SCREEN_ROOT_CAUSE_ROUTE.apiPathPrefix}/${encoded}${GET_SCREEN_ROOT_CAUSE_ROUTE.apiPathSuffix}`;
  const params = new URLSearchParams();
  if (date && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
    params.set("date", date);
  }
  if (asOfIso && asOfIso.trim() !== "") {
    params.set("asOf", asOfIso.trim());
  }
  if (options?.forceRefresh === true) {
    params.set("forceRefresh", "true");
  }
  const q = params.toString();
  return `${base}${path}${q ? `?${q}` : ""}`;
}

/** One-off GET (e.g. regenerate) with optional ClickHouse bypass — same response shape as {@link useGetScreenRootCause}. */
export async function fetchScreenRootCause(params: {
  screenName: string;
  date: string;
  asOfIso: string;
  projectId: string;
  forceRefresh?: boolean;
}): Promise<ApiResponse<ScreenRootCauseData>> {
  const name = String(params.screenName).trim();
  const d = String(params.date).trim();
  const asOf = String(params.asOfIso).trim();
  const trimmedProject = String(params.projectId).trim();
  if (!name) {
    return {
      data: null,
      error: { code: "400", message: "Screen name is required", cause: "" },
      status: 400,
    };
  }
  if (process.env.REACT_APP_USE_MOCK_SERVER === "true") {
    return getMockScreenRootCauseApiResponse(name);
  }
  const url = buildScreenRootCauseUrl(name, d || undefined, asOf || undefined, {
    forceRefresh: params.forceRefresh === true,
  });
  const headers: Record<string, string> = {};
  if (trimmedProject !== "") {
    headers["X-Project-ID"] = trimmedProject;
  }
  return makeRequest<ScreenRootCauseData>({
    url,
    init: {
      method: GET_SCREEN_ROOT_CAUSE_ROUTE.method,
      headers,
    },
  });
}

export function useGetScreenRootCause({
  screenName,
  date,
  asOfIso,
  projectId,
  enabled = true,
}: UseGetScreenRootCauseParams) {
  const trimmedName = screenName != null ? String(screenName).trim() : "";
  const trimmedProject = projectId != null ? String(projectId).trim() : "";
  const d = date != null ? String(date).trim() : "";
  const asOf = asOfIso != null ? String(asOfIso).trim() : "";

  return useQuery({
    queryKey: [
      GET_SCREEN_ROOT_CAUSE_ROUTE.key,
      trimmedName,
      trimmedProject,
      d,
      asOf,
    ],
    queryFn: async (): Promise<ApiResponse<ScreenRootCauseData>> => {
      if (!trimmedName) {
        return {
          data: null,
          error: {
            code: "400",
            message: "Screen name is required",
            cause: "",
          },
          status: 400,
        };
      }
      if (process.env.REACT_APP_USE_MOCK_SERVER === "true") {
        return getMockScreenRootCauseApiResponse(trimmedName);
      }
      const url = buildScreenRootCauseUrl(
        trimmedName,
        d || undefined,
        asOf || undefined,
      );
      const headers: Record<string, string> = {};
      if (trimmedProject !== "") {
        headers["X-Project-ID"] = trimmedProject;
      }
      return makeRequest<ScreenRootCauseData>({
        url,
        init: {
          method: GET_SCREEN_ROOT_CAUSE_ROUTE.method,
          headers,
        },
      });
    },
    enabled:
      enabled &&
      trimmedName !== "" &&
      trimmedProject !== "" &&
      d !== "" &&
      /^\d{4}-\d{2}-\d{2}$/.test(d) &&
      asOf !== "",
    retry: false,
  });
}
