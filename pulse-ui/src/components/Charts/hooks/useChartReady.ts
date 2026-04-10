import { useCallback, useRef } from "react";
import { useFilterStore } from "../../../stores/useFilterStore";
import type { EChartsType } from "echarts";
import { getUTCDateTimeFromLocalStringDateValue } from "../../../utils/DateUtil";
import { useSearchParams } from "react-router-dom";
import { StartEndDateTimeType } from "../../../screens/CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

interface UseChartReadyProps {
  syncTooltips?: boolean;
  group?: string;
  enableBrushSelection?: boolean;
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
  /** When set, maps x-axis category values from brush to filter times (e.g. ISO bucket keys). */
  mapBrushToTimeFilter?: (
    startLabel: string,
    endLabel: string,
  ) => StartEndDateTimeType | null | undefined;
  /**
   * When true with `onTimeFilterChange`, maps ECharts dataZoom on a **time** x-axis (ms) to the
   * global time filter. Category-axis charts must leave this false (indices are not timestamps).
   */
  syncDataZoomToTimeFilter?: boolean;
}

export const useChartReady = ({
  syncTooltips = false,
  group = "default",
  enableBrushSelection = true,
  onTimeFilterChange,
  mapBrushToTimeFilter,
  syncDataZoomToTimeFilter = false,
}: UseChartReadyProps = {}) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const {
    handleDateTimeApply,
    setActiveQuickTimeFilter,
    handleQuickTimeFilterChange,
  } = useFilterStore();

  const dataZoomDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(
    null,
  );

  const applyCustomTimeRange = useCallback(
    (timeFilterValue: StartEndDateTimeType | null | undefined) => {
      if (
        !timeFilterValue?.startDate?.trim() ||
        !timeFilterValue?.endDate?.trim()
      ) {
        return;
      }

      const { startTime: appliedStart, endTime: appliedEnd } =
        useFilterStore.getState();
      if (appliedStart && appliedEnd) {
        const a = dayjs.utc(timeFilterValue.startDate).valueOf();
        const b = dayjs.utc(appliedStart).valueOf();
        const c = dayjs.utc(timeFilterValue.endDate).valueOf();
        const d = dayjs.utc(appliedEnd).valueOf();
        if (
          Number.isFinite(a) &&
          Number.isFinite(b) &&
          Number.isFinite(c) &&
          Number.isFinite(d) &&
          Math.abs(a - b) < 1000 &&
          Math.abs(c - d) < 1000
        ) {
          return;
        }
      }

      setActiveQuickTimeFilter(-1);
      handleQuickTimeFilterChange(-1);
      const newSearchParams = handleDateTimeApply(
        timeFilterValue,
        searchParams,
        onTimeFilterChange || (() => {}),
      );
      setSearchParams(newSearchParams);
    },
    [
      handleDateTimeApply,
      searchParams,
      setSearchParams,
      setActiveQuickTimeFilter,
      handleQuickTimeFilterChange,
      onTimeFilterChange,
    ],
  );

  const handleBrushSelect = useCallback(
    (xMin: number, xMax: number, axisData: string[]) => {
      const startDate = axisData[Math.floor(xMin)];
      const endDate = axisData[Math.floor(xMax)];

      if (startDate && endDate) {
        const timeFilterValue: StartEndDateTimeType | null | undefined =
          mapBrushToTimeFilter
            ? mapBrushToTimeFilter(startDate, endDate)
            : {
                startDate: getUTCDateTimeFromLocalStringDateValue(startDate),
                endDate: getUTCDateTimeFromLocalStringDateValue(endDate),
              };

        applyCustomTimeRange(timeFilterValue);
      }
    },
    [applyCustomTimeRange, mapBrushToTimeFilter],
  );
  const onChartReady = useCallback(
    (chartInstance: EChartsType) => {
      if (syncTooltips) {
        chartInstance.group = group;
      }

      if (enableBrushSelection) {
        chartInstance.dispatchAction({
          type: "brushSelect",
          areas: [],
        });

        chartInstance.dispatchAction({
          type: "takeGlobalCursor",
          key: "brush",
          brushOption: {
            brushType: "lineX",
            brushMode: "single",
          },
        });

        chartInstance.on("brushEnd", function (params: any) {
          if (params.areas && params.areas.length > 0) {
            const area = params.areas[0];

            if (area.coordRanges && area.coordRanges[0]) {
              const [xMin, xMax] = area.coordRanges[0];
              const chartOption = chartInstance.getOption();
              const xAxisArray = chartOption?.xAxis;
              let xAxisData = [];

              if (Array.isArray(xAxisArray) && xAxisArray.length > 0) {
                xAxisData = xAxisArray[0].data || [];
              }
              handleBrushSelect(xMin, xMax, xAxisData);

              chartInstance.dispatchAction({
                type: "dataZoom",
                dataZoomIndex: 0,
                startValue: xMin,
                endValue: xMax,
              });

              chartInstance.dispatchAction({
                type: "brush",
                areas: [],
              });

              chartInstance.dispatchAction({
                type: "takeGlobalCursor",
                key: "brush",
                brushOption: {
                  brushType: "lineX",
                  brushMode: "single",
                },
              });
            }
          }
        });
      }

      if (onTimeFilterChange && syncDataZoomToTimeFilter) {
        const dataZoomIgnoreUntil = Date.now() + 400;
        chartInstance.on("datazoom", function (params: any) {
          if (Date.now() < dataZoomIgnoreUntil) {
            return;
          }
          if (dataZoomDebounceRef.current) {
            clearTimeout(dataZoomDebounceRef.current);
          }
          dataZoomDebounceRef.current = setTimeout(() => {
            dataZoomDebounceRef.current = null;

            let startValue: number | undefined;
            let endValue: number | undefined;

            const batches = params?.batch;
            if (Array.isArray(batches)) {
              for (const b of batches) {
                if (
                  typeof b?.startValue === "number" &&
                  typeof b?.endValue === "number"
                ) {
                  startValue = b.startValue;
                  endValue = b.endValue;
                  break;
                }
              }
            }
            if (
              (startValue === undefined || endValue === undefined) &&
              typeof params?.startValue === "number" &&
              typeof params?.endValue === "number"
            ) {
              startValue = params.startValue;
              endValue = params.endValue;
            }
            if (
              startValue === undefined ||
              endValue === undefined ||
              !Number.isFinite(startValue) ||
              !Number.isFinite(endValue)
            ) {
              const opt = chartInstance.getOption() as {
                dataZoom?: Array<{
                  startValue?: number;
                  endValue?: number;
                }>;
              };
              const dz = opt?.dataZoom?.[0];
              if (
                typeof dz?.startValue === "number" &&
                typeof dz?.endValue === "number"
              ) {
                startValue = dz.startValue;
                endValue = dz.endValue;
              }
            }

            if (
              startValue === undefined ||
              endValue === undefined ||
              startValue >= endValue
            ) {
              return;
            }

            const timeFilterValue: StartEndDateTimeType = {
              startDate: dayjs(startValue).utc().format("YYYY-MM-DD HH:mm:ss"),
              endDate: dayjs(endValue).utc().format("YYYY-MM-DD HH:mm:ss"),
            };

            applyCustomTimeRange(timeFilterValue);
          }, 200);
        });
      }
    },
    [
      syncTooltips,
      group,
      enableBrushSelection,
      handleBrushSelect,
      onTimeFilterChange,
      applyCustomTimeRange,
      syncDataZoomToTimeFilter,
    ],
  );
  return { onChartReady };
};
