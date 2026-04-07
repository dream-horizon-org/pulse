import { useCallback } from "react";
import { useFilterStore } from "../../../stores/useFilterStore";
import type { EChartsType } from "echarts";
import { getUTCDateTimeFromLocalStringDateValue } from "../../../utils/DateUtil";
import { useSearchParams } from "react-router-dom";
import { StartEndDateTimeType } from "../../../screens/CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

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
}

export const useChartReady = ({
  syncTooltips = false,
  group = "default",
  enableBrushSelection = true,
  onTimeFilterChange,
  mapBrushToTimeFilter,
}: UseChartReadyProps = {}) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const {
    handleDateTimeApply,
    setActiveQuickTimeFilter,
    handleQuickTimeFilterChange,
  } = useFilterStore();

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

        if (
          !timeFilterValue?.startDate?.trim() ||
          !timeFilterValue?.endDate?.trim()
        ) {
          return;
        }

        setActiveQuickTimeFilter(-1);
        handleQuickTimeFilterChange(-1);
        const newSearchParams = handleDateTimeApply(
          timeFilterValue,
          searchParams,
          onTimeFilterChange || (() => {}),
        );
        setSearchParams(newSearchParams);
      }
    },
    [
      handleDateTimeApply,
      searchParams,
      setSearchParams,
      setActiveQuickTimeFilter,
      handleQuickTimeFilterChange,
      onTimeFilterChange,
      mapBrushToTimeFilter,
    ],
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
    },
    [syncTooltips, group, enableBrushSelection, handleBrushSelect],
  );
  return { onChartReady };
};
