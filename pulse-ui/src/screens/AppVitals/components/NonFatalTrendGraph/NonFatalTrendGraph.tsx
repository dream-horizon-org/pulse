import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import { TrendGraph } from "../TrendGraph";
import { QueryState } from "../../../../components/QueryState";
import { useQueryError } from "../../../../hooks/useQueryError";
import {
  getBucketSize,
  buildCommonFilters,
  normalizeTrendBucketTime,
} from "../TrendGraphWithData/helpers/trendDataHelpers";
import { COLUMN_NAME } from "../../../../constants/PulseOtelSemcov";
import { NonFatalTrendGraphProps } from "./NonFatalTrendGraph.interface";
import type {
  FilterField,
  DataQueryResponse,
} from "../../../../hooks/useGetDataQuery/useGetDataQuery.interface";

export function NonFatalTrendGraph({
  startTime,
  endTime,
  appVersion = "all",
  osVersion = "all",
  device = "all",
  platform = "all",
  networkProvider = "all",
  state = "all",
  screenName,
  title,
  lineColor,
  onTimeFilterChange,
}: NonFatalTrendGraphProps) {
  const bucketSize = useMemo(
    () => getBucketSize(startTime, endTime),
    [startTime, endTime],
  );

  const filters = useMemo(() => {
    const filterArray: FilterField[] = [
      {
        field: "PulseType",
        operator: "EQ",
        value: ["non_fatal"],
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

    const commonFilters = buildCommonFilters(
      appVersion,
      osVersion,
      device,
      platform,
      networkProvider,
      state,
    );
    filterArray.push(...commonFilters);

    return filterArray;
  }, [
    appVersion,
    osVersion,
    device,
    platform,
    networkProvider,
    state,
    screenName,
  ]);

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
          param: { bucket: bucketSize, field: COLUMN_NAME.TIMESTAMP },
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
        bucketTime: normalizeTrendBucketTime(timestamp),
        count,
      };
    });
  }, [data]);

  return (
    <QueryState
      isLoading={queryState.isLoading}
      isError={queryState.isError}
      errorMessage={queryState.errorMessage}
      errorType={queryState.errorInfo?.type}
      emptyMessage="No non-fatal trend data available"
      skeletonTitle={title}
      skeletonHeight={225}
    >
      <TrendGraph
        data={trendData}
        bucketSize={bucketSize}
        title={title}
        dataKey="count"
        lineColor={lineColor}
        rangeStart={startTime}
        rangeEnd={endTime}
        onTimeFilterChange={onTimeFilterChange}
      />
    </QueryState>
  );
}
