import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import type { ApiResponse } from "../../helpers/makeRequest";
import { GET_SCREEN_ROOT_CAUSE_ROUTE } from "../../constants/API";
import type {
  ScreenRootCauseData,
  UseGetScreenRootCauseParams,
} from "./useGetScreenRootCause.interface";

function buildScreenRootCauseUrl(
  screenName: string,
  windowStartIso: string | undefined,
  windowEndIso: string | undefined,
  date: string | undefined,
  asOfIso: string | undefined,
): string {
  const base = getApiBaseUrl().replace(/\/$/, "");
  const encoded = encodeURIComponent(screenName);
  const path = `${GET_SCREEN_ROOT_CAUSE_ROUTE.apiPathPrefix}/${encoded}${GET_SCREEN_ROOT_CAUSE_ROUTE.apiPathSuffix}`;
  const params = new URLSearchParams();
  const explicit =
    windowStartIso != null &&
    windowStartIso.trim() !== "" &&
    windowEndIso != null &&
    windowEndIso.trim() !== "";
  if (explicit) {
    params.set("start", windowStartIso.trim());
    params.set("end", windowEndIso.trim());
  } else {
    if (date && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
      params.set("date", date);
    }
    if (asOfIso && asOfIso.trim() !== "") {
      params.set("asOf", asOfIso);
    }
  }
  const q = params.toString();
  return `${base}${path}${q ? `?${q}` : ""}`;
}

export function useGetScreenRootCause({
  screenName,
  windowStartIso,
  windowEndIso,
  date,
  asOfIso,
  projectId,
  enabled = true,
}: UseGetScreenRootCauseParams) {
  const trimmedName = screenName != null ? String(screenName).trim() : "";
  const trimmedProject = projectId != null ? String(projectId).trim() : "";
  const ws = windowStartIso != null ? String(windowStartIso).trim() : "";
  const we = windowEndIso != null ? String(windowEndIso).trim() : "";
  const useExplicit = ws !== "" && we !== "";

  return useQuery({
    queryKey: [
      GET_SCREEN_ROOT_CAUSE_ROUTE.key,
      trimmedName,
      trimmedProject,
      useExplicit ? "explicit" : "legacy",
      useExplicit ? ws : (date ?? ""),
      useExplicit ? we : (asOfIso ?? ""),
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
      const url = buildScreenRootCauseUrl(
        trimmedName,
        ws || undefined,
        we || undefined,
        date ?? undefined,
        asOfIso ?? undefined,
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
      (useExplicit ||
        (date != null && date !== "") ||
        (asOfIso != null && asOfIso !== "")),
    retry: false,
  });
}
