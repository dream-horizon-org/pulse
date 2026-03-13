import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL } from "../../constants";
import { IncidentItem } from "./useGetIncidents.interface";

export function useGetIncidents(enabled: boolean) {
  return useQuery({
    queryKey: ["incidents"],
    queryFn: () =>
      makeRequest<IncidentItem[]>({
        url: `${API_BASE_URL}/v1/incidents`,
        init: { method: "GET" },
      }),
    enabled,
    refetchOnWindowFocus: false,
  });
}
