import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";

export const useEventCategories = () => {
  const route = API_ROUTES.GET_EVENT_CATEGORIES;

  return useQuery({
    queryKey: [route.key],
    queryFn: async () => {
      return makeRequest<string[]>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: { method: route.method },
      });
    },
    refetchOnWindowFocus: false,
    staleTime: 60_000,
  });
};
