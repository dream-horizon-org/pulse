import { TrendGraph } from "../TrendGraph";
import { useTrendData } from "../TrendGraphWithData/hooks/useTrendData";
import { QueryState } from "../../../../components/QueryState";
import type { CrashTrendGraphProps } from "./CrashTrendGraph.interface";

export function CrashTrendGraph({
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
}: CrashTrendGraphProps) {
  const { trendData, queryState, bucketSize } = useTrendData({
    startTime,
    endTime,
    eventName: "device.crash",
    appVersion,
    osVersion,
    device,
    platform,
    networkProvider,
    state,
    screenName,
  });

  return (
    <QueryState
      isLoading={queryState.isLoading}
      isError={queryState.isError}
      errorMessage={queryState.errorMessage}
      errorType={queryState.errorInfo?.type}
      emptyMessage="No crash trend data available"
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
