import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GetDataQueryParams,
  DataQueryResponse,
} from "./useGetDataQuery.interface";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

/** Parse UI time strings to UTC ISO. Returns null if empty or unparseable (avoids Invalid Date → toISOString throw). */
function tryFormatTimeToIso(time: string): string | null {
  const trimmed = typeof time === "string" ? time.trim() : "";
  if (!trimmed) return null;
  if (trimmed.includes("T") || trimmed.includes("Z")) {
    const d = dayjs.utc(trimmed);
    return d.isValid() ? d.toISOString() : null;
  }
  const withFormat = dayjs.utc(trimmed, "YYYY-MM-DD HH:mm:ss");
  if (withFormat.isValid()) return withFormat.toISOString();
  const loose = dayjs.utc(trimmed);
  return loose.isValid() ? loose.toISOString() : null;
}

export const useGetDataQuery = ({
  requestBody,
  enabled = true,
  refetchInterval = false,
}: GetDataQueryParams) => {
  const dataQuery = API_ROUTES.DATA_QUERY;
  const formattedStartTime = tryFormatTimeToIso(requestBody.timeRange.start);
  const formattedEndTime = tryFormatTimeToIso(requestBody.timeRange.end);
  const hasValidTimeRange =
    formattedStartTime != null && formattedEndTime != null;
  const isProjectReady = useProjectQueryEnabled(enabled && hasValidTimeRange);

  const modifiedRequestBody = {
    ...requestBody,
    timeRange: {
      start: formattedStartTime ?? "",
      end: formattedEndTime ?? "",
    },
  };

  return useQuery({
    queryKey: [
      dataQuery.key,
      requestBody.dataType,
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      JSON.stringify(requestBody.select),
      JSON.stringify(requestBody.groupBy),
      JSON.stringify(requestBody.filters),
    ],
    queryFn: async () => {
      return makeRequest<DataQueryResponse>({
        url: `${API_BASE_URL}${dataQuery.apiPath}`,
        init: {
          method: dataQuery.method,
          body: JSON.stringify(modifiedRequestBody),
        },
      });
    },
    refetchOnWindowFocus: false,
    refetchInterval,
    enabled: isProjectReady,
    staleTime: 10000,
    placeholderData: undefined,
  });
};
