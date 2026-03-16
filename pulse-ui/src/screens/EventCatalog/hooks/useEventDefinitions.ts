import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";
import { getQueryParamString } from "../../../helpers/queryParams";
import { EventDefinitionListResponse } from "../EventCatalog.types";

type UseEventDefinitionsParams = {
  search?: string;
  category?: string;
  limit?: number;
  offset?: number;
};

export const useEventDefinitions = ({
  search = "",
  category = "",
  limit = 50,
  offset = 0,
}: UseEventDefinitionsParams) => {
  const route = API_ROUTES.GET_EVENT_DEFINITIONS;

  const queryParams = getQueryParamString({
    ...(search ? { search } : {}),
    ...(category ? { category } : {}),
    limit: String(limit),
    offset: String(offset),
  });

  return useQuery({
    queryKey: [route.key, search, category, limit, offset],
    queryFn: async () => {
      return makeRequest<EventDefinitionListResponse>({
        url: `${API_BASE_URL}${route.apiPath}${queryParams}`,
        init: {
          method: route.method,
        },
      });
    },
    refetchOnWindowFocus: false,
  });
};
