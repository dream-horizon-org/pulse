import { useQuery } from "@tanstack/react-query";
import {
  fetchJourneysList,
  FunnelJourneyListQueryParams,
} from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

function stableQueryKey(params: FunnelJourneyListQueryParams): string {
  return JSON.stringify({
    search: params.search ?? "",
    status: params.status ?? "",
    createdBy: params.createdBy?.slice().sort().join(",") ?? "",
    tags: params.tags?.slice().sort().join(",") ?? "",
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 10,
  });
}

export const useGetJourneysList = ({
  queryParams,
}: {
  queryParams: FunnelJourneyListQueryParams;
}) => {
  const enabled = useProjectQueryEnabled();
  const key = stableQueryKey(queryParams);

  return useQuery({
    queryKey: ["journeysList", key],
    queryFn: () => fetchJourneysList(queryParams),
    enabled,
    refetchOnWindowFocus: false,
  });
};
