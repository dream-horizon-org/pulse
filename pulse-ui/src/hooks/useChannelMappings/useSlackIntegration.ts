import { useMutation, useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import { GetSlackChannelsResponse } from "./useChannelMappings.interface";

type SlackInstallParams = {
  returnPath?: string;
};

export const useGetSlackInstallUrl = () => {
  const apiRoute = API_ROUTES.SLACK_INSTALL;

  return useMutation({
    mutationFn: async (params?: SlackInstallParams) =>
      makeRequest<string>({
        url: `${API_BASE_URL}${apiRoute.apiPath}${
          params?.returnPath
            ? `?returnPath=${encodeURIComponent(params.returnPath)}`
            : ""
        }`,
        init: {
          method: apiRoute.method,
        },
      }),
  });
};

export const useGetSlackChannels = () => {
  const apiRoute = API_ROUTES.SLACK_CHANNELS;
  const enabled = useProjectQueryEnabled();

  return useQuery({
    queryKey: [apiRoute.key],
    queryFn: async () =>
      makeRequest<GetSlackChannelsResponse>({
        url: `${API_BASE_URL}${apiRoute.apiPath}`,
        init: {
          method: apiRoute.method,
        },
      }),
    enabled,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};
