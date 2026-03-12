import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  RegenerateApiKeyParams,
  CreateApiKeyResponse,
  ApiKeyListResponse,
} from "./useProjectApiKeys.interface";

export const useRegenerateProjectApiKey = () => {
  const queryClient = useQueryClient();
  const getRoute = API_ROUTES.GET_PROJECT_API_KEYS;
  const createRoute = API_ROUTES.CREATE_PROJECT_API_KEY;
  const revokeRoute = API_ROUTES.REVOKE_PROJECT_API_KEY;

  return useMutation<ApiResponse<CreateApiKeyResponse>, unknown, RegenerateApiKeyParams>({
    mutationFn: async (params: RegenerateApiKeyParams) => {
      let currentDisplayName = 'Default Key';

      // Step 1: If regenerating, revoke old key first
      if (params.isRegenerate) {
        // Get current active key to find its ID and display name
        const listResponse = await makeRequest<ApiKeyListResponse>({
          url: `${API_BASE_URL}${getRoute.apiPath.replace(':projectId', params.projectId)}`,
          init: { method: getRoute.method },
        });

        if (listResponse.data?.apiKeys) {
          const activeKey = listResponse.data.apiKeys.find((k) => k.isActive);

          if (activeKey) {
            currentDisplayName = activeKey.displayName || 'Default Key';

            // Revoke the old key with 0-day grace period (immediate revocation)
            await makeRequest({
              url: `${API_BASE_URL}${revokeRoute.apiPath
                .replace(':projectId', params.projectId)
                .replace(':apiKeyId', String(activeKey.apiKeyId))}`,
              init: {
                method: revokeRoute.method,
                headers: {
                  "Content-Type": "application/json",
                },
                body: JSON.stringify({ gracePeriodDays: 0 }),
              },
            });
          }
        }
      }

      // Step 2: Create new key
      const createResponse = await makeRequest<CreateApiKeyResponse>({
        url: `${API_BASE_URL}${createRoute.apiPath.replace(':projectId', params.projectId)}`,
        init: {
          method: createRoute.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            displayName: currentDisplayName,
            expiresAt: null,
          }),
        },
      });

      if (createResponse.error || !createResponse.data) {
        throw new Error(createResponse.error?.message ?? 'Failed to create API key');
      }

      return createResponse;
    },
    onSuccess: (data: ApiResponse<CreateApiKeyResponse>, variables: RegenerateApiKeyParams) => {
      if (data?.data && !data?.error) {
        queryClient.invalidateQueries({
          queryKey: [getRoute.key, variables.projectId],
        });
      }
    },
  });
};
