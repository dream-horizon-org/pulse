import { useQuery } from '@tanstack/react-query';
import { API_BASE_URL, API_ROUTES } from '../../constants';
import { makeRequest } from '../../helpers/makeRequest';
import { AllConfigDetailsResponse, GetAllSdkConfigsParams } from './useSdkConfig.interface';

/**
 * Hook to fetch all SDK configuration versions
 * GET /v1/configs
 */
export const useGetAllSdkConfigs = ({ enabled = true }: GetAllSdkConfigsParams = {}) => {
  const route = API_ROUTES.GET_ALL_SDK_CONFIGS;

  return useQuery({
    queryKey: [route.key],
    queryFn: async () => {
      return makeRequest<AllConfigDetailsResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
        },
      });
    },
    enabled,
    refetchOnWindowFocus: false,
    staleTime: 30000, // 30 seconds
  });
};

