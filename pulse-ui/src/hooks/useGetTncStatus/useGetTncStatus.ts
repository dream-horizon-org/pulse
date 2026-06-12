import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { TncStatusResponse } from "./useGetTncStatus.interface";

export const useGetTncStatus = (enabled: boolean = true) => {
  const route = API_ROUTES.GET_TNC_STATUS;

  return useQuery({
    queryKey: [route.key],
    queryFn: async () => {
      return makeRequest<TncStatusResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
        },
      });
    },
    enabled,
    retry: false,
    staleTime: 0,
  });
};

export const useGetTncDocuments = (enabled: boolean = true) => {
  const route = API_ROUTES.GET_TNC_DOCUMENTS;

  return useQuery({
    queryKey: [route.key],
    queryFn: async () => {
      return makeRequest<TncStatusResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
        },
      });
    },
    enabled,
    retry: false,
    staleTime: 0,
  });
};
