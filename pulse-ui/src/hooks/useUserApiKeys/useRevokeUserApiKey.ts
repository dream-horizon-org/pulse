import { useMutation, useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";

export const useRevokeUserApiKey = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (keyId: number) => {
      const route = API_ROUTES.REVOKE_USER_API_KEY;
      await makeRequest({
        url: `${API_BASE_URL}${route.apiPath.replace(":keyId", String(keyId))}`,
        init: { method: route.method },
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [API_ROUTES.LIST_USER_API_KEYS.key] });
    },
  });
};
