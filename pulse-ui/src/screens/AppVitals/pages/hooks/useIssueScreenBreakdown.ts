import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import { useQueryError } from "../../../../hooks/useQueryError";
import type { DataQueryResponse } from "../../../../hooks/useGetDataQuery/useGetDataQuery.interface";

interface ScreenBreakdown {
  screen: string;
  occurrences: number;
  percentage: number;
}

interface UseIssueScreenBreakdownParams {
  groupId: string;
  startTime?: string;
  endTime?: string;
}

export function useIssueScreenBreakdown({
  groupId,
  startTime,
  endTime,
}: UseIssueScreenBreakdownParams) {
  // Build filters - filter by GroupId
  const filters = useMemo(() => {
    const filterArray = [];

    // Filter by GroupId
    if (groupId) {
      filterArray.push({
        field: "GroupId",
        operator: "EQ" as const,
        value: [groupId],
      });
    }

    return filterArray.length > 0 ? filterArray : undefined;
  }, [groupId]);

  // Memoize time range to prevent unnecessary re-renders
  const timeRange = useMemo(() => {
    if (startTime && endTime) {
      return {
        start: startTime,
        end: endTime,
      };
    }
    // Default to last 7 days
    return {
      start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
      end: new Date().toISOString(),
    };
  }, [startTime, endTime]);

  // Memoize request body
  const requestBody = useMemo(
    () => ({
      dataType: "EXCEPTIONS" as const,
      timeRange,
      filters,
      select: [
        {
          function: "COL" as const,
          param: {
            field: "ScreenName",
          },
          alias: "screen_name",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "count()",
          },
          alias: "occurrences",
        },
      ],
      groupBy: ["screen_name"],
      orderBy: [
        {
          field: "occurrences",
          direction: "DESC" as const,
        },
      ],
      limit: 10,
    }),
    [timeRange, filters],
  );

  // Fetch screen breakdown
  const queryResult = useGetDataQuery({
    requestBody,
    enabled: !!groupId,
  });

  const { data } = queryResult;
  const queryState = useQueryError<DataQueryResponse>({ queryResult });

  // Transform API response to ScreenBreakdown format
  const screenBreakdown: ScreenBreakdown[] = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const fields = responseData.fields;
    const screenNameIndex = fields.indexOf("screen_name");
    const occurrencesIndex = fields.indexOf("occurrences");

    // Calculate total occurrences
    const total = responseData.rows.reduce((sum, row) => {
      return sum + (parseFloat(row[occurrencesIndex]) || 0);
    }, 0);

    return responseData.rows.map((row) => {
      const screen = row[screenNameIndex] || "Unknown Screen";
      const occurrences = parseFloat(row[occurrencesIndex]) || 0;
      const percentage =
        total > 0 ? Math.round((occurrences / total) * 100) : 0;

      return {
        screen,
        occurrences: Math.round(occurrences),
        percentage,
      };
    });
  }, [data]);

  return {
    screenBreakdown,
    queryState,
  };
}
