import { useMutation, useQueryClient } from '@tanstack/react-query';
import { API_BASE_URL, API_ROUTES } from '../../constants';
import { ApiResponse, makeRequest } from '../../helpers/makeRequest';
import { CreateConfigResponse, CreateSdkConfigInput, OnCreateSettled } from './useSdkConfig.interface';
import { stripUIFields } from '../../screens/SamplingConfig/SamplingConfig.constants';

/**
 * Hook to create a new SDK configuration version
 * POST /v1/configs
 */
export const useCreateSdkConfig = (onSettled?: OnCreateSettled) => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.CREATE_SDK_CONFIG;

  return useMutation<
    ApiResponse<CreateConfigResponse>,
    unknown,
    CreateSdkConfigInput
  >({
    mutationFn: async ({ config }: CreateSdkConfigInput) => {
      // Strip UI-only fields before sending to API
      const cleanConfig = stripUIFields(config);
      
      return makeRequest<CreateConfigResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
          body: JSON.stringify(cleanConfig),
        },
      });
    },
    onSuccess: () => {
      // Invalidate config queries to refetch fresh data
      queryClient.invalidateQueries({ queryKey: [API_ROUTES.GET_ALL_SDK_CONFIGS.key] });
      queryClient.invalidateQueries({ queryKey: [API_ROUTES.GET_ACTIVE_SDK_CONFIG.key] });
    },
    onSettled: (data, error) => {
      onSettled?.(data?.data ?? undefined, error);
    },
  });
};

