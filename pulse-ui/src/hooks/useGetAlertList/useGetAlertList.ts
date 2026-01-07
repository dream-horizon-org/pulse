import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { getQueryParamString } from "../../helpers/queryParams";
import {
  GetAlertListQueryParams,
  GetAlertListResponse,
} from "./useGetAlertList.interface";
import { makeRequest } from "../../helpers/makeRequest";

export const useGetAlertList = ({
  queryParams = null,
}: GetAlertListQueryParams) => {
  const getAlertList = API_ROUTES.GET_ALERTS;
  const filteredQueryParams = filterNonNullParams(queryParams);

  const searchParams = getQueryParamString(filteredQueryParams);

  return useQuery({
    queryKey: [getAlertList.key, searchParams.toString()],
    queryFn: async () => {
      return makeRequest<GetAlertListResponse>({
        url: `${API_BASE_URL}${getAlertList.apiPath}${searchParams}`,
        init: {
          method: getAlertList.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    refetchInterval: 30000,
  });
};

function filterNonNullParams(
  params: GetAlertListQueryParams["queryParams"],
): Partial<GetAlertListQueryParams["queryParams"]> {
  if (!params) {
    return {};
  }

  // Filter out the entries with null values
  const filteredEntries = Object.entries(params).filter(
    ([_, value]) => value !== null,
  );

  // Rebuild the object without null values
  return Object.fromEntries(filteredEntries);
}




