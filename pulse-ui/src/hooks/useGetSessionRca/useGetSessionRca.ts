import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import type { ApiResponse } from "../../helpers/makeRequest";
import { GET_SESSION_RCA_ROUTE } from "../../constants/API";
import type { SessionRcaData, UseGetSessionRcaParams } from "./useGetSessionRca.interface";

function buildUrl(
  date: string | undefined,
  asOfIso: string | undefined,
  forceRefresh?: boolean,
): string {
  const base = getApiBaseUrl().replace(/\/$/, "");
  const params = new URLSearchParams();
  if (date && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
    params.set("date", date);
  }
  if (asOfIso && asOfIso.trim() !== "") {
    params.set("asOf", asOfIso.trim());
  }
  if (forceRefresh === true) {
    params.set("forceRefresh", "true");
  }
  const q = params.toString();
  return `${base}${GET_SESSION_RCA_ROUTE.apiPath}${q ? `?${q}` : ""}`;
}

export async function fetchSessionRca(params: {
  date: string;
  asOfIso: string;
  projectId: string;
  forceRefresh?: boolean;
}): Promise<ApiResponse<SessionRcaData>> {
  const url = buildUrl(params.date, params.asOfIso, params.forceRefresh);
  return makeRequest<SessionRcaData>({
    url,
    init: {
      method: GET_SESSION_RCA_ROUTE.method,
      headers: { "X-Project-ID": params.projectId },
    },
  });
}

export function useGetSessionRca({
  date,
  asOfIso,
  projectId,
  enabled = true,
}: UseGetSessionRcaParams) {
  const d = date != null ? String(date).trim() : "";
  const asOf = asOfIso != null ? String(asOfIso).trim() : "";
  const pid = projectId != null ? String(projectId).trim() : "";

  return useQuery({
    queryKey: [GET_SESSION_RCA_ROUTE.key, pid, d, asOf],
    queryFn: async (): Promise<ApiResponse<SessionRcaData>> => {
      const url = buildUrl(d || undefined, asOf || undefined);
      return makeRequest<SessionRcaData>({
        url,
        init: {
          method: GET_SESSION_RCA_ROUTE.method,
          headers: pid !== "" ? { "X-Project-ID": pid } : {},
        },
      });
    },
    enabled: enabled && pid !== "" && d !== "" && /^\d{4}-\d{2}-\d{2}$/.test(d) && asOf !== "",
    retry: false,
  });
}
