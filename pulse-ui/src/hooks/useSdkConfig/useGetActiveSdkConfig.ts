import { useQuery } from '@tanstack/react-query';
import { API_BASE_URL, API_ROUTES } from '../../constants';
import { makeRequest } from '../../helpers/makeRequest';
import { PulseConfig, GetActiveSdkConfigParams } from './useSdkConfig.interface';

/**
 * Hook to fetch the currently active SDK configuration
 * GET /v1/configs/active
 */
export const useGetActiveSdkConfig = ({ enabled = true }: GetActiveSdkConfigParams = {}) => {
  const route = API_ROUTES.GET_ACTIVE_SDK_CONFIG;

  return useQuery({
    queryKey: [route.key],
    queryFn: async () => {
      return makeRequest<PulseConfig>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
        },
      });
    },
    enabled,
    refetchOnWindowFocus: false,
    staleTime: 60000, // 1 minute
  });
};

