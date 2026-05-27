import { Box } from "@mantine/core";
import { useMemo } from "react";
import { LineChart } from "../../../components/Charts";
import {
  trendRangeSpansMultipleUtcDays,
  trendBrushSelectionToTimeFilter,
} from "./TrendGraphWithData/helpers/trendDataHelpers";
import { formatTimeToLocalFromUTCString } from "../../../utils/DateUtil";
import type { TimeBucketSize } from "../../../utils/TimeBucketUtil";
import type { StartEndDateTimeType } from "../../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

interface TrendDataPoint {
  label: string;
  bucketTime?: string;
  count?: number;
  [key: string]: any;
}

interface ChartColors {
  appVersion: string[];
  os: string[];
}

interface OccurrenceTrendChartProps {
  trendData: TrendDataPoint[];
  trendView: string;
  chartColors: ChartColors;
  bucketSize: TimeBucketSize;
  rangeStart?: string;
  rangeEnd?: string;
  getXAxisInterval: () => number;
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
}

export const OccurrenceTrendChart: React.FC<OccurrenceTrendChartProps> = ({
  trendData,
  trendView,
  chartColors,
  bucketSize,
  rangeStart,
  rangeEnd,
  getXAxisInterval,
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

  const { appVersions, osVersions } = useMemo(() => {
    if (!trendData || trendData.length === 0) {
      return { appVersions: [], osVersions: [] };
    }

    const allKeys = new Set<string>();
    trendData.forEach((point) => {
      Object.keys(point).forEach((key) => {
        if (key !== "label" && key !== "count" && key !== "bucketTime") {
          allKeys.add(key);
        }
      });
    });

    const keysArray = Array.from(allKeys).sort();

    return {
      appVersions: keysArray,
      osVersions: keysArray,
    };
  }, [trendData]);

  const generateSeries = () => {
    if (trendView === "aggregated") {
      return [
        {
          name: "Occurrences",
          color: "#0ec9c2",
          data: trendData.map((d) => d.count || 0),
        },
      ];
    } else if (trendView === "appVersion") {
      if (appVersions.length === 0) {
        return [];
      }
      return appVersions.map((version, idx) => ({
        name: version,
        color:
          chartColors.appVersion[idx % chartColors.appVersion.length] ||
          "#0ec9c2",
        data: trendData.map((d) => d[version] || 0),
      }));
    } else if (trendView === "os") {
      if (osVersions.length === 0) {
        return [];
      }
      return osVersions.map((os, idx) => ({
        name: os,
        color: chartColors.os[idx % chartColors.os.length] || "#0ec9c2",
        data: trendData.map((d) => d[os] || 0),
      }));
    }
    return [];
  };

  const xCategories = trendData.map((d) => d.bucketTime || d.label);

  return (
    <Box style={{ height: 262, width: "100%" }}>
      <LineChart
        height={262}
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
          xAxis: {
            type: "category",
            data: xCategories,
            axisLabel: {
              interval: getXAxisInterval(),
              formatter: (value: string) =>
                formatTimeToLocalFromUTCString(value, bucketSize),
            },
          },
          yAxis: {
            type: "value",
          },
          series: generateSeries(),
        }}
      />
    </Box>
  );
};
