import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import {
  GetUserExperimentsParams,
  UserExperimentsResponse,
} from "./useGetUserExperiments.interface";

export const useGetUserExperiments = ({
  pathParams,
}: GetUserExperimentsParams) => {
  const userExperiments = API_ROUTES.GET_USER_EXPERIMENTS;

  return useQuery({
    queryKey: [userExperiments.key, pathParams?.phoneNo],
    queryFn: async () => {
      // Don't validate if phoneNo is empty (initial state)
      if (pathParams?.phoneNo === null || pathParams?.phoneNo === undefined) {
        return {
          data: null,
          error: {
            code: "",
            message: "Please enter a valid phone number",
            cause: "",
          },
        };
      }

      // Only validate length if phoneNo is not empty
      if (pathParams?.phoneNo.length > 0 && pathParams?.phoneNo.length !== 10) {
        return {
          data: null,
          error: {
            code: "",
            message: "Please enter a valid phone number",
            cause: "",
          },
        };
      }

      // If phoneNo is empty, return null data (no error)
      if (pathParams?.phoneNo.length === 0) {
        return {
          data: null,
          error: null,
        };
      }

      return makeRequest<UserExperimentsResponse>({
        url: `${API_BASE_URL}${userExperiments.apiPath.replace("{phoneNo}", pathParams?.phoneNo)}`,
        init: {
          method: userExperiments.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    enabled: false,
  });
};
