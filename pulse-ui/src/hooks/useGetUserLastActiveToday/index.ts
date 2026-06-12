import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  GetUserLastActiveTodayParams,
  UserLastActiveTodayResponse,
} from "./useGetUserLastActiveToday.interface";

export const useGetUserLastActiveToday = ({
  pathParams,
}: GetUserLastActiveTodayParams) => {
  const userLastActiveToday = API_ROUTES.GET_USER_LAST_ACTIVE_TODAY;

  return useQuery({
    queryKey: [userLastActiveToday.key, pathParams?.phoneNo],
    queryFn: async () => {
      if (pathParams?.phoneNo === null) {
        return {} as ApiResponse<UserLastActiveTodayResponse>;
      }

      return makeRequest<UserLastActiveTodayResponse>({
        url: `${API_BASE_URL}${userLastActiveToday.apiPath.replace("{phoneNo}", pathParams?.phoneNo)}`,
        init: {
          method: userLastActiveToday.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    enabled: false,
  });
};
