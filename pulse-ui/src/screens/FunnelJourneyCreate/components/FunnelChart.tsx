import ReactECharts from "echarts-for-react";
import { FunnelStepResult } from "../../../hooks/useGetFunnelData";

interface FunnelChartProps {
  steps: FunnelStepResult[];
}

export function FunnelChart({ steps }: FunnelChartProps) {
  const maxCount = steps.length > 0 ? steps[0].count : 1;

  const option = {
    tooltip: {
      trigger: "item",
      formatter: (params: any) => {
        const step = steps[params.dataIndex];
        return `
          <strong>${step.stepName}</strong><br/>
          Users: <strong>${step.count.toLocaleString()}</strong><br/>
          Conversion: <strong>${step.conversionRate}%</strong><br/>
          Drop-off: <strong>${step.dropoffRate}%</strong>
        `;
      },
    },
    series: [
      {
        type: "funnel",
        left: "10%",
        top: 20,
        bottom: 20,
        width: "80%",
        min: 0,
        max: maxCount,
        minSize: "10%",
        maxSize: "100%",
        sort: "descending",
        gap: 4,
        label: {
          show: true,
          position: "inside",
          formatter: (params: any) => {
            const step = steps[params.dataIndex];
            return `${step.stepName}\n${step.count.toLocaleString()} (${step.conversionRate}%)`;
          },
          fontSize: 13,
          fontWeight: 600,
          color: "#fff",
          lineHeight: 20,
        },
        labelLine: {
          length: 10,
          lineStyle: {
            width: 1,
            type: "solid",
          },
        },
        itemStyle: {
          borderColor: "#fff",
          borderWidth: 2,
          borderRadius: 4,
        },
        emphasis: {
          label: {
            fontSize: 14,
          },
        },
        data: steps.map((step, index) => ({
          value: step.count,
          name: step.stepName,
          itemStyle: {
            color: getStepColor(index, steps.length),
          },
        })),
      },
    ],
  };

  return (
    <ReactECharts
      option={option}
      style={{ height: "400px", width: "100%" }}
      notMerge
    />
  );
}

function getStepColor(index: number, total: number): string {
  const colors = [
    "#0ec9c2",
    "#10b4ae",
    "#0ba09a",
    "#098b86",
    "#077672",
    "#05615e",
    "#044c4a",
  ];
  const colorIndex = Math.min(
    Math.floor((index / Math.max(total - 1, 1)) * (colors.length - 1)),
    colors.length - 1
  );
  return colors[colorIndex];
}
