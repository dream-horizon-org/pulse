import { useQuery } from '@tanstack/react-query';
import { API_BASE_URL, API_ROUTES } from '../../constants';
import { makeRequest } from '../../helpers/makeRequest';
import { ScopesAndSdksResponse } from './useSdkConfig.interface';

/**
 * Hook to fetch available scopes and SDKs from backend
 * GET /v1/configs/scopes-sdks
 */
export const useGetSdkScopesAndSdks = (enabled = true) => {
  const route = API_ROUTES.GET_SDK_SCOPES_AND_SDKS;

  return useQuery({
    queryKey: [route.key],
    queryFn: async () => {
      return makeRequest<ScopesAndSdksResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
        },
      });
    },
    enabled,
    refetchOnWindowFocus: false,
    staleTime: 300000, // 5 minutes (rarely changes)
  });
};

