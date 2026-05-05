import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { UserApiKeyListItem } from "./useUserApiKeys.interface";

export const useListUserApiKeys = () => {
  const route = API_ROUTES.LIST_USER_API_KEYS;
  return useQuery<UserApiKeyListItem[]>({
    queryKey: [route.key],
    queryFn: async () => {
      const resp = await makeRequest<UserApiKeyListItem[]>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: { method: route.method },
      });
      return resp.data ?? [];
    },
    refetchOnWindowFocus: false,
  });
};
