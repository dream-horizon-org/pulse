import { useMemo } from "react";
import { useGetDataQuery } from "../../../../../hooks";
import {
  getBucketSize,
  formatTrendDate,
  buildCommonFilters,
} from "../helpers/trendDataHelpers";
import { useQueryError } from "../../../../../hooks/useQueryError";
import type {
  FilterField,
  DataQueryResponse,
} from "../../../../../hooks/useGetDataQuery/useGetDataQuery.interface";

interface UseTrendDataParams {
  startTime: string;
  endTime: string;
  eventName: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  screenName?: string;
}

export function useTrendData({
  startTime,
  endTime,
  eventName,
  appVersion = "all",
  osVersion = "all",
  device = "all",
  screenName,
}: UseTrendDataParams) {
  const bucketSize = useMemo(
    () => getBucketSize(startTime, endTime),
    [startTime, endTime],
  );

  const filters = useMemo(() => {
    const filterArray: FilterField[] = [
      {
        field: "PulseType",
        operator: "EQ",
        value: [eventName],
      },
    ];

    // Add screen name filter if provided
    if (screenName) {
      filterArray.push({
        field: "ScreenName",
        operator: "EQ",
        value: [screenName],
      });
    }

    const commonFilters = buildCommonFilters(appVersion, osVersion, device);
    filterArray.push(...commonFilters);

    return filterArray;
  }, [eventName, appVersion, osVersion, device, screenName]);

  const queryResult = useGetDataQuery({
    requestBody: {
      dataType: "EXCEPTIONS",
      timeRange: {
        start: startTime,
        end: endTime,
      },
      select: [
        {
          function: "TIME_BUCKET",
          param: { bucket: bucketSize, field: "Timestamp" },
          alias: "t1",
        },
        {
          function: "CUSTOM",
          param: { expression: "count()" },
          alias: "count",
        },
      ],
      filters,
      groupBy: ["t1"],
      orderBy: [{ field: "t1", direction: "ASC" }],
    },
    enabled: !!startTime && !!endTime,
  });

  const { data } = queryResult;
  const queryState = useQueryError<DataQueryResponse>({ queryResult });

  const trendData = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const fields = responseData.fields;
    const timeIndex = fields.indexOf("t1");
    const countIndex = fields.indexOf("count");

    return responseData.rows.map((row) => {
      const timestamp = row[timeIndex];
      const count = parseFloat(row[countIndex]) || 0;

      return {
        date: formatTrendDate(timestamp, bucketSize),
        count,
      };
    });
  }, [data, bucketSize]);

  return { trendData, queryState };
}
