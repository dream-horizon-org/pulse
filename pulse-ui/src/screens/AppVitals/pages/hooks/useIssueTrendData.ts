import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import { useQueryError } from "../../../../hooks/useQueryError";
import type {
  DataQueryResponse,
  SelectField,
} from "../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import {
  getBucketSize,
  formatTrendDate,
  buildCommonFilters,
} from "../../components/TrendGraphWithData/helpers/trendDataHelpers";
import dayjs from "dayjs";
import { COLUMN_NAME } from "../../../../constants/PulseOtelSemcov";
interface UseIssueTrendDataParams {
  groupId: string;
  startTime?: string;
  endTime?: string;
  trendView: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
}

interface TrendDataPoint {
  label: string;
  count?: number;
  [key: string]: any;
}

export function useIssueTrendData({
  groupId,
  startTime,
  endTime,
  trendView,
  appVersion = "all",
  osVersion = "all",
  device = "all",
}: UseIssueTrendDataParams) {
  // Memoize time range
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

  const bucketSize = useMemo(
    () => getBucketSize(timeRange.start, timeRange.end),
    [timeRange.start, timeRange.end],
  );

  // Build filters - filter by GroupId
  const filters = useMemo(() => {
    const filterArray = [
      {
        field: "GroupId",
        operator: "EQ" as const,
        value: [groupId],
      },
    ];

    // Add common filters (appVersion, osVersion, device)
    const commonFilters = buildCommonFilters(appVersion, osVersion, device);
    filterArray.push(...commonFilters);

    return filterArray;
  }, [groupId, appVersion, osVersion, device]);

  // Build select fields based on trendView
  const selectFields = useMemo((): SelectField[] => {
    const baseFields: SelectField[] = [
      {
        function: "TIME_BUCKET" as const,
        param: { bucket: bucketSize, field: "Timestamp" },
        alias: "t1",
      },
      {
        function: "CUSTOM" as const,
        param: { expression: "count()" },
        alias: "count",
      },
    ];

    if (trendView === "appVersion") {
      baseFields.push({
        function: "COL" as const,
        param: { field: COLUMN_NAME.APP_VERSION },
        alias: "app_version",
      });
    } else if (trendView === "os") {
      baseFields.push({
        function: "COL" as const,
        param: { field: "OsVersion" },
        alias: "os_version",
      });
    }

    return baseFields;
  }, [bucketSize, trendView]);

  // Build groupBy based on trendView
  const groupBy = useMemo(() => {
    if (trendView === "aggregated") {
      return ["t1"];
    } else if (trendView === "appVersion") {
      return ["t1", "app_version"];
    } else if (trendView === "os") {
      return ["t1", "os_version"];
    }
    return ["t1"];
  }, [trendView]);

  // Memoize request body
  const requestBody = useMemo(
    () => ({
      dataType: "EXCEPTIONS" as const,
      timeRange,
      filters,
      select: selectFields,
      groupBy,
      orderBy: [{ field: "t1", direction: "ASC" as const }],
    }),
    [timeRange, filters, selectFields, groupBy],
  );

  // Fetch trend data
  const queryResult = useGetDataQuery({
    requestBody,
    enabled: !!groupId,
  });

  const { data } = queryResult;
  const queryState = useQueryError<DataQueryResponse>({ queryResult });

  // Transform API response to trend data format
  const trendData: TrendDataPoint[] = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const fields = responseData.fields;
    const timeIndex = fields.indexOf("t1");
    const countIndex = fields.indexOf("count");
    const appVersionIndex =
      trendView === "appVersion" ? fields.indexOf("app_version") : -1;
    const osVersionIndex = trendView === "os" ? fields.indexOf("os_version") : -1;

    // Group data by time bucket
    const timeBucketMap = new Map<string, TrendDataPoint>();

    responseData.rows.forEach((row) => {
      const timestamp = row[timeIndex] || "";
      const count = parseFloat(row[countIndex]) || 0;
      const label = formatTrendDate(timestamp, bucketSize);

      // Get the breakdown value (app version or OS version)
      let breakdownValue: string | undefined;
      if (trendView === "appVersion" && appVersionIndex >= 0) {
        breakdownValue = row[appVersionIndex] || "";
      } else if (trendView === "os" && osVersionIndex >= 0) {
        breakdownValue = row[osVersionIndex] || "";
      }

      // For aggregated view, just sum counts per time bucket
      if (trendView === "aggregated") {
        const existing = timeBucketMap.get(timestamp);
        if (existing) {
          existing.count = (existing.count || 0) + count;
        } else {
          timeBucketMap.set(timestamp, {
            label,
            count,
          });
        }
      } else if (breakdownValue) {
        // For breakdown views, create/update entry for this time bucket
        const existing = timeBucketMap.get(timestamp);
        if (existing) {
          existing[breakdownValue] = count;
        } else {
          const point: TrendDataPoint = {
            label,
            [breakdownValue]: count,
          };
          timeBucketMap.set(timestamp, point);
        }
      }
    });

    // Convert map to array and sort by timestamp
    const result = Array.from(timeBucketMap.values());
    
    // Sort by label (which is the formatted date)
    // We need to parse the label back to timestamp for proper sorting
    result.sort((a, b) => {
      // Try to parse the label, fallback to string comparison
      const dateA = dayjs(a.label).valueOf();
      const dateB = dayjs(b.label).valueOf();
      if (!isNaN(dateA) && !isNaN(dateB)) {
        return dateA - dateB;
      }
      return a.label.localeCompare(b.label);
    });

    return result;
  }, [data, bucketSize, trendView]);

  return {
    trendData,
    queryState,
  };
}

