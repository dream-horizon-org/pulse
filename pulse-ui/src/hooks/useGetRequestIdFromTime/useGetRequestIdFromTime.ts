import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import {
  getQueryParamString,
  removeUndefinedOrNullValues,
} from "../../helpers/queryParams";
import {
  GetRequestIdFromTimeRequestBody,
  GetRequestIdFromTimeSuccessResponseBody,
} from "./useGetRequestIdFromTime.interface";

export const useGetRequestIdFromTime = ({
  from_date,
  to_date,
  email,
  pattern,
  userId,
}: GetRequestIdFromTimeRequestBody) => {
  const getRequestIdFromTime = API_ROUTES.GET_REQUEST_ID_BY_TIME;

  // Filter out null/undefined values and create query params
  const queryParams = {
    from_date,
    to_date,
    email,
    pattern,
    userId,
  };

  const searchParams = getQueryParamString(
    removeUndefinedOrNullValues(queryParams),
  );

  return useQuery({
    queryKey: [
      getRequestIdFromTime.key,
      from_date,
      to_date,
      email,
      pattern,
      userId,
    ],
    queryFn: async () => {
      // Validate required parameters
      if (!from_date || !to_date) {
        return {
          data: null,
          error: {
            code: "VALIDATION_ERROR",
            message:
              "All parameters (from_date, to_date, email, eventName, userId) are required",
            cause: "Missing required parameters",
          },
        };
      }

      return makeRequest<GetRequestIdFromTimeSuccessResponseBody>({
        url: `${API_BASE_URL}${getRequestIdFromTime.apiPath}${searchParams}`,
        init: {
          method: getRequestIdFromTime.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    // Only enable the query if all required parameters are provided
    enabled: false,
  });
};
