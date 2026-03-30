import { useQuery } from "@tanstack/react-query";
import {
  fetchFunnelsJourneysList,
  FunnelsJourneysListQueryParams,
} from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

function stableQueryKey(params: FunnelsJourneysListQueryParams): string {
  return JSON.stringify({
    kind: params.kind ?? "",
    search: params.search ?? "",
    status: params.status ?? "",
    createdBy: params.createdBy?.slice().sort().join(",") ?? "",
    tags: params.tags?.slice().sort().join(",") ?? "",
    funnelType: params.funnelType ?? "",
  });
}

export const useGetFunnelsJourneysList = ({
  queryParams,
}: {
  queryParams: FunnelsJourneysListQueryParams;
}) => {
  const enabled = useProjectQueryEnabled();
  const key = stableQueryKey(queryParams);

  return useQuery({
    queryKey: ["funnelsJourneysList", key],
    queryFn: () => fetchFunnelsJourneysList(queryParams),
    enabled,
    refetchOnWindowFocus: false,
  });
};
