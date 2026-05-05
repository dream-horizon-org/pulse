import { useMutation, useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { CreateUserApiKeyResponse } from "./useUserApiKeys.interface";

export const useCreateUserApiKey = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (displayName: string) => {
      const route = API_ROUTES.CREATE_USER_API_KEY;
      const resp = await makeRequest<CreateUserApiKeyResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ displayName }),
        },
      });
      if (!resp.data) throw new Error(resp.error?.message ?? "Failed to create API key");
      return resp.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [API_ROUTES.LIST_USER_API_KEYS.key] });
    },
  });
};
