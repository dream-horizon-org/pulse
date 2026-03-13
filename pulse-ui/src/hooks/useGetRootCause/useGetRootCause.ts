import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import type {
  RootCauseResponse,
  UseGetRootCauseParams,
} from "./useGetRootCause.interface";
import { getMockRootCauseResponse } from "./getMockRootCauseResponse";

export function useGetRootCause({
  interactionName,
  date,
  enabled = true,
}: UseGetRootCauseParams) {
  const route = API_ROUTES.GET_INTERACTION_ROOT_CAUSE;

  return useQuery({
    queryKey: [route.key, interactionName, date ?? null],
    queryFn: async (): Promise<RootCauseResponse | null> => {
      if (!interactionName?.trim()) {
        return null;
      }
      const encodedName = encodeURIComponent(interactionName.trim());
      const url = new URL(
        `${API_BASE_URL}/v1/interactions/${encodedName}/root-cause`,
      );
      if (date) {
        url.searchParams.set("date", date);
      }
      const result = await makeRequest<RootCauseResponse>({
        url: url.toString(),
        init: { method: route.method },
      });
      if (result.error || result.status >= 400) {
        // When backend is not ready (404/501), return mock so UI can be developed
        if (
          result.status === 404 ||
          result.status === 501 ||
          result.status === 0
        ) {
          return getMockRootCauseResponse(interactionName);
        }
        throw new Error(result.error?.message ?? "Failed to load root cause");
      }
      return result.data ?? null;
    },
    enabled: enabled && !!interactionName?.trim(),
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
  });
}
