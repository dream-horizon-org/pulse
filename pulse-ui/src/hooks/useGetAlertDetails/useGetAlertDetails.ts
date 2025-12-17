import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { GetAlertDetailsParams, AlertDetailsResponse } from "./useGetAlertDetails.interface";

export const useGetAlertDetails = ({
  queryParams = null,
  refetchInterval = false,
}: GetAlertDetailsParams) => {
  const getAlertDetails = API_ROUTES.GET_ALERT_DETAILS;

  return useQuery({
    queryKey: [getAlertDetails.key, queryParams?.alert_id],
    queryFn: async () => {
      if (
        queryParams?.alert_id === null ||
        isNaN(Number(queryParams?.alert_id))
      ) {
        return {} as ApiResponse<AlertDetailsResponse>;
      }

      return makeRequest<AlertDetailsResponse>({
        url: `${API_BASE_URL}${getAlertDetails.apiPath}/${queryParams?.alert_id}`,
        init: {
          method: getAlertDetails.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    refetchInterval,
    staleTime: 0,
  });
};



