import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetJobStatusResponse } from "./useGetJobStatus.interface";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";

export const useGetJobStatus = (jobId: number = 0, enabled: boolean = true) => {
  const getJobStatus = API_ROUTES.GET_JOB_STATUS;
  return useQuery({
    queryKey: [getJobStatus.key, jobId],
    queryFn: () => {
      if (jobId < 1) {
        return {} as ApiResponse<GetJobStatusResponse>;
      }

      return makeRequest<GetJobStatusResponse>({
        url: `${API_BASE_URL}${getJobStatus.apiPath}?jobId=${jobId}`,
        init: {
          method: getJobStatus.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    enabled: enabled,
  });
};
