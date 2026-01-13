import { useQuery } from '@tanstack/react-query';
import { API_BASE_URL, API_ROUTES } from '../../constants';
import { makeRequest } from '../../helpers/makeRequest';
import { PulseConfig, GetSdkConfigByVersionParams } from './useSdkConfig.interface';

/**
 * Hook to fetch SDK configuration by version
 * GET /v1/configs/{version}
 */
export const useGetSdkConfigByVersion = ({ 
  version, 
  enabled = true 
}: GetSdkConfigByVersionParams) => {
  const route = API_ROUTES.GET_SDK_CONFIG_BY_VERSION;

  return useQuery({
    queryKey: [route.key, version],
    queryFn: async () => {
      if (version === null) {
        return { data: null };
      }
      
      const apiPath = route.apiPath.replace('{version}', String(version));
      return makeRequest<PulseConfig>({
        url: `${API_BASE_URL}${apiPath}`,
        init: {
          method: route.method,
        },
      });
    },
    enabled: enabled && version !== null,
    refetchOnWindowFocus: false,
    staleTime: 60000, // 1 minute
  });
};

