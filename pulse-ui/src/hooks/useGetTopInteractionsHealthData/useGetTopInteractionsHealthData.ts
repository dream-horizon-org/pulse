import { useMemo } from "react";
import { useGetDataQuery } from "../useGetDataQuery";
import { useGetInteractions } from "../useGetInteractions";
import { COLUMN_NAME, PulseType } from "../../constants/PulseOtelSemcov";
import { classifyError, getErrorMessage } from "../../utils/errorHandling";
import {
  UseGetTopInteractionsHealthDataProps,
  TopInteractionHealthData,
} from "./useGetTopInteractionsHealthData.interface";

/** Rows to request from ClickHouse so we can still fill `limit` after filtering to RDBMS-registered names. */
const CLICKHOUSE_CANDIDATE_LIMIT = (displayLimit: number) =>
  Math.min(500, Math.max(displayLimit * 50, 100));

/** Max interactions to load from pulse-server (MySQL) for the registered-name allowlist. */
const REGISTERED_INTERACTIONS_PAGE_SIZE = 1000;

export function useGetTopInteractionsHealthData({
  startTime,
  endTime,
  limit = 4,
}: UseGetTopInteractionsHealthDataProps): {
  data: TopInteractionHealthData[];
  isLoading: boolean;
  error: string | null;
} {
  const timeRangeReady = !!startTime && !!endTime;

  const {
    data: registeredInteractionsResponse,
    isLoading: isLoadingRegisteredNames,
    isError: isInteractionsRequestFailed,
    error: interactionsRequestError,
  } = useGetInteractions({
    queryParams: {
      page: 0,
      size: REGISTERED_INTERACTIONS_PAGE_SIZE,
    },
    pageIdentifier: "top-interactions-health-registered-names",
    enabled: timeRangeReady,
  });

  const interactionsListErrorMessage = useMemo(() => {
    if (!timeRangeReady) {
      return null;
    }
    if (registeredInteractionsResponse?.error) {
      return getErrorMessage(
        classifyError(
          registeredInteractionsResponse,
          registeredInteractionsResponse.status,
        ),
      );
    }
    if (isInteractionsRequestFailed && interactionsRequestError) {
      return getErrorMessage(classifyError(interactionsRequestError));
    }
    return null;
  }, [
    timeRangeReady,
    registeredInteractionsResponse,
    isInteractionsRequestFailed,
    interactionsRequestError,
  ]);

  const registeredInteractionNames = useMemo(() => {
    const names =
      registeredInteractionsResponse?.data?.interactions?.map((row) => row.name) ?? [];
    return new Set(names);
  }, [registeredInteractionsResponse]);

  // Fetch top interactions from ClickHouse (candidates), then keep only names present in MySQL.
  const { data, isLoading: isLoadingClickHouse } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: {
        start: startTime,
        end: endTime,
      },
      select: [
        {
          function: "COL",
          param: { field: COLUMN_NAME.SPAN_NAME },
          alias: "interaction_name",
        },
        {
          function: "CUSTOM",
          param: { expression: "COUNT()" },
          alias: "spanfreq",
        },
        { function: "APDEX", alias: "apdex" },
        { function: "INTERACTION_SUCCESS_COUNT", alias: "success_count" },
        { function: "INTERACTION_ERROR_COUNT", alias: "error_count" },
        { function: "USER_CATEGORY_EXCELLENT", alias: "user_excellent" },
        { function: "USER_CATEGORY_GOOD", alias: "user_good" },
        { function: "USER_CATEGORY_AVERAGE", alias: "user_avg" },
        { function: "USER_CATEGORY_POOR", alias: "user_poor" },
        { function: "DURATION_P50", alias: "p50" },
      ],
      filters: [
        {
          field: COLUMN_NAME.PULSE_TYPE,
          operator: "EQ",
          value: [PulseType.INTERACTION],
        },
      ],
      groupBy: ["interaction_name"],
      orderBy: [{ field: "spanfreq", direction: "DESC" }],
      limit: CLICKHOUSE_CANDIDATE_LIMIT(limit),
    },
    enabled: timeRangeReady,
  });

  const isLoading = isLoadingRegisteredNames || isLoadingClickHouse;

  // Transform API response to card props
  const topInteractionsData = useMemo<TopInteractionHealthData[]>(() => {
    if (interactionsListErrorMessage) {
      return [];
    }

    if (registeredInteractionNames.size === 0) {
      return [];
    }

    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const fields = responseData.fields;
    const interactionNameIndex = fields.indexOf("interaction_name");
    const apdexIndex = fields.indexOf("apdex");
    const successCountIndex = fields.indexOf("success_count");
    const errorCountIndex = fields.indexOf("error_count");
    const userExcellentIndex = fields.indexOf("user_excellent");
    const userGoodIndex = fields.indexOf("user_good");
    const userAvgIndex = fields.indexOf("user_avg");
    const userPoorIndex = fields.indexOf("user_poor");
    const p50Index = fields.indexOf("p50");

    const mapped: TopInteractionHealthData[] = [];

    for (const row of responseData.rows) {
      const interactionName = row[interactionNameIndex];
      if (!registeredInteractionNames.has(interactionName)) {
        continue;
      }

      const successCount = parseFloat(row[successCountIndex]) || 0;
      const errorCount = parseFloat(row[errorCountIndex]) || 0;
      const totalRequests = successCount + errorCount;

      const userExcellent = parseFloat(row[userExcellentIndex]) || 0;
      const userGood = parseFloat(row[userGoodIndex]) || 0;
      const userAvg = parseFloat(row[userAvgIndex]) || 0;
      const userPoor = parseFloat(row[userPoorIndex]) || 0;
      const totalUsers = userExcellent + userGood + userAvg + userPoor;

      mapped.push({
        id: mapped.length,
        interactionName,
        apdex: parseFloat(row[apdexIndex]) || 0,
        errorRate: totalRequests > 0 ? (errorCount / totalRequests) * 100 : 0,
        p50: parseFloat(row[p50Index]) || 0,
        poorUserPercentage: totalUsers > 0 ? (userPoor / totalUsers) * 100 : 0,
      });

      if (mapped.length >= limit) {
        break;
      }
    }

    return mapped;
  }, [
    data,
    interactionsListErrorMessage,
    limit,
    registeredInteractionNames,
  ]);

  return {
    data: topInteractionsData,
    isLoading,
    error: interactionsListErrorMessage,
  };
}

