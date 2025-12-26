import { useMemo } from "react";
import { useMantineTheme } from "@mantine/core";
import { CustomToolTip } from "../Tooltip";

interface UseChartOptionsProps {
  tooltip?: any;
  withLegend?: boolean;
  zoom?: boolean;
  chartType?: "line" | "area";
}

export const useChartOptions = ({
  tooltip = CustomToolTip,
  withLegend = true,
  zoom = true,
  chartType = "line",
}: UseChartOptionsProps = {}) => {
  const theme = useMantineTheme();

  const defaultOptions = useMemo(
    () => ({
      tooltip: {
        ...tooltip,
        show: false,
      },
      toolbox: {
        show: false,
      },
      ...(withLegend && {
        legend: {
          bottom: 10,
          right: 25,
          padding: [7, 10],
          itemGap: 15,
          textStyle: {
            fontSize: 14,
            color: theme.black,
            fontFamily: theme.fontFamily,
          },
          icon: "circle",
        },
      }),
      grid: {
        top: "20",
        left: "25",
        right: "25",
        bottom: "50",
        containLabel: true,
      },
      xAxis: {
        ...(chartType === "area" && { type: "category" }),
        // Use percentage string for non-ordinal axes to avoid ECharts warning
        boundaryGap: chartType === "area" ? false : ["0%", "0%"],
        axisLabel: {
          margin: 15,
          fontSize: 12,
          fontFamily: theme.fontFamily,
          showMinLabel: true,
          showMaxLabel: true,
          alignMaxLabel: "right",
          align: "center",
          color: theme.colors.gray[6],
          formatter: (value: any, index: number) =>
            index === 0 ? `{minLabel|${value}}` : value,
          rich: {
            minLabel: {
              padding: [0, 0, 0, 20],
            },
          },
          hideOverlap: true,
        },
        axisPointer: {
          show: true,
          type: "line",
          label: {
            backgroundColor: theme.colors.blue[6],
          },
        },
      },
      yAxis: {
        ...(chartType === "area" && { type: "value" }),
        splitNumber: 4,
        axisLabel: {
          color: theme.colors.gray[6],
          fontFamily: theme.fontFamily,
        },
        splitLine: {
          show: true,
          lineStyle: {
            type: "dashed",
            width: 1.25,
          },
        },
      },
      ...(zoom && {
        dataZoom: [
          {
            type: "inside",
            xAxisIndex: 0,
            yAxisIndex: false,
            start: 0,
            end: 100,
            minValueSpan: 2,
            minSpan: 2,
            filterMode: "none",
            zoomOnMouseWheel: false,
            moveOnMouseMove: false,
            moveOnMouseWheel: false,
            preventDefaultMouseMove: false,
          },
        ],
      }),
      brush: {
        xAxisIndex: 0,
        brushType: "lineX",
        brushStyle: {
          color: "rgba(120,140,180,0.3)",
          borderColor: "rgba(120,140,180,0.8)",
          borderWidth: 1,
        },
        transformable: true,
        z: 10001,
        brushMode: "single",
        removeOnClick: false,
      },
    }),
    [theme, withLegend, zoom, chartType, tooltip],
  );

  const createMergedOptions = useMemo(
    () => (option: any) => ({
      ...defaultOptions,
      ...option,
      tooltip: {
        ...defaultOptions.tooltip,
        ...option?.tooltip,
      },
      ...(withLegend &&
        defaultOptions.legend &&
        option?.legend && {
          legend: {
            ...defaultOptions.legend,
            ...option.legend,
          },
        }),
      ...(!withLegend && { legend: undefined }),
      xAxis: Array.isArray(option?.xAxis)
        ? option.xAxis.map((xAxisItem: any) => ({
            ...defaultOptions.xAxis,
            ...xAxisItem,
            axisLabel: {
              ...defaultOptions.xAxis.axisLabel,
              ...xAxisItem?.axisLabel,
            },
          }))
        : {
            ...defaultOptions.xAxis,
            ...option?.xAxis,
            axisLabel: {
              ...defaultOptions.xAxis.axisLabel,
              ...option?.xAxis?.axisLabel,
            },
          },
      yAxis: Array.isArray(option?.yAxis)
        ? option.yAxis.map((yAxisItem: any) => ({
            ...defaultOptions.yAxis,
            ...yAxisItem,
            axisLabel: {
              ...defaultOptions.yAxis.axisLabel,
              ...yAxisItem?.axisLabel,
            },
            splitLine: {
              ...defaultOptions.yAxis.splitLine,
              ...yAxisItem?.splitLine,
              lineStyle: {
                ...defaultOptions.yAxis.splitLine.lineStyle,
                ...yAxisItem?.splitLine?.lineStyle,
              },
            },
          }))
        : {
            ...defaultOptions.yAxis,
            ...option?.yAxis,
            axisLabel: {
              ...defaultOptions.yAxis.axisLabel,
              ...option?.yAxis?.axisLabel,
            },
            splitLine: {
              ...defaultOptions.yAxis.splitLine,
              ...option?.yAxis?.splitLine,
              lineStyle: {
                ...defaultOptions.yAxis.splitLine.lineStyle,
                ...option?.yAxis?.splitLine?.lineStyle,
              },
            },
          },
      brush: {
        ...defaultOptions.brush,
        ...option?.brush,
      },
      ...(zoom && option?.dataZoom && { dataZoom: option.dataZoom }),
      ...(!zoom && { dataZoom: undefined }),
    }),
    [defaultOptions, withLegend, zoom],
  );

  return {
    defaultOptions,
    createMergedOptions,
  };
};
