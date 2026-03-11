import { useMutation, useQueryClient } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { AcceptTncParams, AcceptTncResponse, UseAcceptTncOptions } from "./useAcceptTnc.interface";

export const useAcceptTnc = (options: UseAcceptTncOptions = {}) => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.ACCEPT_TNC;

  return useMutation({
    mutationFn: (params: AcceptTncParams) => {
      return makeRequest<AcceptTncResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(params),
        },
      });
    },
    onSettled: (data, error, variables, context) => {
      if (data?.data && !data?.error) {
        queryClient.invalidateQueries({
          queryKey: [API_ROUTES.GET_TNC_STATUS.key],
        });
      }
      options.onSettled?.(data, error, variables, context);
    },
  });
};
