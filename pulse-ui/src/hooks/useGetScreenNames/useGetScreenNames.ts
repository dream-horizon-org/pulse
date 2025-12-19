import { SpanType } from "../../constants/PulseOtelSemcov";
import { OperatorType, useGetDataQuery } from "../useGetDataQuery";
import { useMemo } from "react";

interface UseGetScreenNamesParams {
  startTime: string;
  endTime: string;
  searchStr?: string;
  enabled?: boolean;
}

export const useGetScreenNames = ({
  startTime,
  endTime,
  searchStr = "",
  enabled = true,
}: UseGetScreenNamesParams) => {
  // Memoize the top screens request body to prevent infinite loops
  // When searching, increase limit to get more results for filtering
  const topScreensRequestBody = useMemo(() => {
    const filters: Array<{
      field: string;
      operator: OperatorType;
      value: string | string[];
    }> = [];

    // Add search filter if search string is provided
    // Note: Since LIKE operator is not available in the FilterField interface,
    // we'll add the search string as a filter value. The API might support
    // partial matching, or we'll filter client-side as fallback
    if (searchStr && searchStr.trim()) {
      // Add filter with search string
      // The API may support text search even without explicit LIKE operator
      filters.push({
        field: `SpanAttributes['${SpanType.SCREEN_NAME}']`,
        operator: "LIKE" as const,
        // Send search string - API might handle partial matching
        // If not, we filter client-side in the screenNames useMemo
        value: [`%${searchStr.trim()}%`],
      });
    }

    filters.push({
      field: "SpanType",
      operator: "IN" as const,
      value: ["screen_session", "screen_load"],
    });

    const limit = searchStr ? 100 : 15;

    const selectFields = [
      {
        function: "COL" as const,
        param: { field: `SpanAttributes['${SpanType.SCREEN_NAME}']` },
        alias: "screen_name",
      },
      {
        function: "CUSTOM" as const,
        param: { expression: "COUNT()" },
        alias: "screen_count",
      },
    ];

    const requestBody: {
      dataType: "TRACES";
      timeRange: { start: string; end: string };
      select: Array<{
        function: "COL" | "CUSTOM";
        param: { field?: string; expression?: string };
        alias: string;
      }>;
      groupBy: string[];
      orderBy: Array<{ field: string; direction: "DESC" }>;
      limit: number;
      filters?: Array<{
        field: string;
        operator: OperatorType;
        value: string | string[];
      }>;
    } = {
      dataType: "TRACES" as const,
      timeRange: {
        start: startTime,
        end: endTime,
      },
      select: selectFields,
      groupBy: ["screen_name"],
      orderBy: [
        {
          field: "screen_count",
          direction: "DESC" as const,
        },
      ],
      filters,
      limit,
    };

    // Only include filters if the array is not empty and has valid filters
    const validFilters = filters.filter((f) =>
      Array.isArray(f.value) ? f.value.length > 0 : !!f.value,
    );
    if (validFilters.length > 0) {
      requestBody.filters = validFilters;
    }

    return requestBody;
  }, [startTime, endTime, searchStr]);

  // Fetch screens (more results when searching)
  const {
    data: topScreensData,
    isLoading,
    isError,
  } = useGetDataQuery({
    requestBody: topScreensRequestBody,
    enabled: enabled && !!startTime && !!endTime,
  });

  // Extract and filter screen names
  const screenNames = useMemo(() => {
    if (!topScreensData?.data?.rows || topScreensData.data.rows.length === 0) {
      return [];
    }
    const fields = topScreensData.data.fields;
    const screenNameIndex = fields.indexOf("screen_name");
    const allScreenNames = topScreensData.data.rows
      .map((row) => row[screenNameIndex])
      .filter((name): name is string => Boolean(name && name.trim()));

    // Filter by search string (client-side filtering since LIKE operator not available)
    if (!searchStr) {
      return allScreenNames;
    }

    const searchLower = searchStr.toLowerCase();
    return allScreenNames.filter((name) =>
      name.toLowerCase().includes(searchLower),
    );
  }, [topScreensData, searchStr]);

  return {
    screenNames,
    isLoading,
    isError,
  };
};
