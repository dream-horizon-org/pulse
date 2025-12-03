import { useMemo } from "react";
import { useGetDataQuery } from "../useGetDataQuery";
import { COLUMN_NAME, SpanType } from "../../constants/PulseOtelSemcov";
import {
  UseGetTopInteractionsHealthDataProps,
  TopInteractionHealthData,
} from "./useGetTopInteractionsHealthData.interface";

export function useGetTopInteractionsHealthData({
  startTime,
  endTime,
  limit = 4,
}: UseGetTopInteractionsHealthDataProps): {
  data: TopInteractionHealthData[];
  isLoading: boolean;
  error: Error | null;
} {
  // Fetch top interactions data
  const { data, isLoading } = useGetDataQuery({
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
          field: COLUMN_NAME.SPAN_TYPE,
          operator: "EQ",
          value: [SpanType.INTERACTION],
        },
      ],
      groupBy: ["interaction_name"],
      orderBy: [{ field: "spanfreq", direction: "DESC" }],
      limit,
    },
    enabled: !!startTime && !!endTime,
  });

  // Transform API response to card props
  const topInteractionsData = useMemo<TopInteractionHealthData[]>(() => {
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

    return responseData.rows.map((row, index) => {
      const successCount = parseFloat(row[successCountIndex]) || 0;
      const errorCount = parseFloat(row[errorCountIndex]) || 0;
      const totalRequests = successCount + errorCount;

      const userExcellent = parseFloat(row[userExcellentIndex]) || 0;
      const userGood = parseFloat(row[userGoodIndex]) || 0;
      const userAvg = parseFloat(row[userAvgIndex]) || 0;
      const userPoor = parseFloat(row[userPoorIndex]) || 0;
      const totalUsers = userExcellent + userGood + userAvg + userPoor;

      return {
        id: index,
        interactionName: row[interactionNameIndex],
        apdex: parseFloat(row[apdexIndex]) || 0,
        errorRate: totalRequests > 0 ? (errorCount / totalRequests) * 100 : 0,
        p50: parseFloat(row[p50Index]) || 0,
        poorUserPercentage: totalUsers > 0 ? (userPoor / totalUsers) * 100 : 0,
      };
    });
  }, [data]);

  const error = null; // You can enhance this to capture errors from queries if needed

  return {
    data: topInteractionsData,
    isLoading,
    error,
  };
}

