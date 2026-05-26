import { Box, Text, Paper } from "@mantine/core";
import { useMemo } from "react";
import { CustomToolTip, createTooltipFormatter, LineChart } from "../../../components/Charts";
import classes from "./TrendGraph.module.css";
import {
  trendRangeSpansMultipleUtcDays,
  trendBrushSelectionToTimeFilter,
} from "./TrendGraphWithData/helpers/trendDataHelpers";
import type { TimeBucketSize } from "../../../utils/TimeBucketUtil";
import type { StartEndDateTimeType } from "../../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import { formatTimeToLocalFromUTCString } from "../../../utils/DateUtil";

export interface TrendGraphDataPoint {
  bucketTime: string;
  count: number;
}

interface TrendGraphProps {
  data: TrendGraphDataPoint[];
  bucketSize: TimeBucketSize;
  title: string;
  dataKey?: string;
  lineColor?: string;
  /** ISO range (same as data query); used for x-axis label density and rotation. */
  rangeStart?: string;
  rangeEnd?: string;
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
}

export const TrendGraph: React.FC<TrendGraphProps> = ({
  data = [],
  bucketSize,
  title = "Trend",
  dataKey = "count",
  lineColor = "#0ec9c2",
  rangeStart,
  rangeEnd,
  onTimeFilterChange,
}) => {
  const multiDay =
    rangeStart &&
    rangeEnd &&
    trendRangeSpansMultipleUtcDays(rangeStart, rangeEnd);

  const mapBrushToTimeFilter = useMemo(
    () =>
      onTimeFilterChange
        ? (startIso: string, endIso: string) =>
            trendBrushSelectionToTimeFilter(startIso, endIso, bucketSize)
        : undefined,
    [onTimeFilterChange, bucketSize],
  );

  if (data.length === 0) {
    return (
      <Paper withBorder p="md" mb="lg">
        <Text fw={600} size="lg" mb="md">
          {title}
        </Text>
        <Text c="dimmed" ta="center" py="xl">
          No data available
        </Text>
      </Paper>
    );
  }

  return (
    <Paper withBorder p="md" mb="lg" className={classes.trendCard}>
      <Box className={classes.topAccent} />
      <Text className={classes.graphTitle}>{title}</Text>
      <Box style={{ height: 225 }}>
        <LineChart
          height={225}
          onTimeFilterChange={onTimeFilterChange}
          mapBrushToTimeFilter={mapBrushToTimeFilter}
          option={{
            grid: {
              top: "20",
              left: "25",
              right: "25",
              bottom: multiDay ? 56 : 50,
              containLabel: true,
            },
            tooltip: {
              ...CustomToolTip,
              trigger: "axis",
              confine: true,
              formatter: createTooltipFormatter({
                valueFormatter: (value: number) => value.toLocaleString(),
                customHeaderFormatter: (axisValue: any) =>
                  axisValue
                    ? formatTimeToLocalFromUTCString(
                        String(axisValue),
                        bucketSize,
                      )
                    : "",
              }),
            },
            xAxis: {
              type: "category",
              data: data.map((d) => d.bucketTime),
              axisLabel: {
                formatter: (value: string) =>
                  formatTimeToLocalFromUTCString(value, bucketSize),
              },
            },
            yAxis: {
              type: "value",
            },
            series: [
              {
                name: "Occurrences",
                color: lineColor,
                data: data.map((d) => d[dataKey as keyof TrendGraphDataPoint]),
              },
            ],
          }}
        />
      </Box>
    </Paper>
  );
};
