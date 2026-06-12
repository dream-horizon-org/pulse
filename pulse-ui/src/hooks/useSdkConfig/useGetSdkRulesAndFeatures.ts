import { useQuery } from '@tanstack/react-query';
import { API_BASE_URL, API_ROUTES } from '../../constants';
import { makeRequest } from '../../helpers/makeRequest';
import { RulesAndFeaturesResponse } from './useSdkConfig.interface';

/**
 * Hook to fetch available rules and features from backend
 * GET /v1/configs/rules-features
 */
export const useGetSdkRulesAndFeatures = (enabled = true) => {
  const route = API_ROUTES.GET_SDK_RULES_AND_FEATURES;

  return useQuery({
    queryKey: [route.key],
    queryFn: async () => {
      return makeRequest<RulesAndFeaturesResponse>({
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

