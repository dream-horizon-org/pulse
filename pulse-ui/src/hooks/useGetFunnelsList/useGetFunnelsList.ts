import { useQuery } from "@tanstack/react-query";
import {
  fetchFunnelsList,
  FunnelJourneyListQueryParams,
} from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

function stableQueryKey(params: FunnelJourneyListQueryParams): string {
  return JSON.stringify({
    search: params.search ?? "",
    status: params.status ?? "",
    createdBy: params.createdBy?.slice().sort().join(",") ?? "",
    tags: params.tags?.slice().sort().join(",") ?? "",
    funnelType: params.funnelType ?? "",
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 10,
  });
}

export const useGetFunnelsList = ({
  queryParams,
}: {
  queryParams: FunnelJourneyListQueryParams;
}) => {
  const enabled = useProjectQueryEnabled();
  const key = stableQueryKey(queryParams);

  return useQuery({
    queryKey: ["funnelsList", key],
    queryFn: () => fetchFunnelsList(queryParams),
    enabled,
    refetchOnWindowFocus: false,
  });
};
