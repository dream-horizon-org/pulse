import type { NetworkRequest } from "../../../../../services/sessionReplay/mockSessionDetail";
import { CHART_LABELS, CHART_TOOLTIPS } from "../../../constants/strings";

export function createWaterfallOption(networkRequests: NetworkRequest[]) {
  if (networkRequests.length === 0) {
    return {
      xAxis: { type: "value" },
      yAxis: { type: "category", data: [] },
      series: [],
    };
  }

  const requests = networkRequests.map((req, idx) => ({
    name: req.url.split("/").pop() || req.url.substring(0, 30),
    start: req.timestamp,
    duration: req.duration,
    status: req.status,
    method: req.method,
    index: idx,
  }));

  const maxTime = Math.max(...requests.map((r) => r.start + r.duration));
  const minTime = Math.min(...requests.map((r) => r.start));

  return {
    tooltip: {
      trigger: "axis",
      formatter: (params: any) => {
        const param = params[0];
        const req = requests[param.dataIndex];
        return CHART_TOOLTIPS.WATERFALL_FORMAT.replace("{method}", req.method)
          .replace("{name}", req.name)
          .replace("{status}", req.status.toString())
          .replace("{duration}", req.duration.toString());
      },
    },
    xAxis: {
      type: "value",
      name: CHART_LABELS.TIME_MS,
      min: 0,
      max: maxTime - minTime,
    },
    yAxis: {
      type: "category",
      data: requests.map((r) => r.name),
      inverse: true,
    },
    series: [
      {
        name: CHART_LABELS.REQUEST_DURATION,
        type: "bar",
        data: requests.map((r) => ({
          value: [r.start - minTime, r.start - minTime + r.duration],
          itemStyle: {
            color:
              r.status >= 200 && r.status < 300
                ? "#0ec9c2"
                : r.status >= 500
                  ? "#ef4444"
                  : "#f59e0b",
          },
        })),
        barWidth: 20,
      },
    ],
  };
}

export function createStatusDistributionOption(
  networkRequests: NetworkRequest[],
) {
  const statusCounts: Record<number, number> = {};
  networkRequests.forEach((req) => {
    const statusGroup =
      req.status >= 200 && req.status < 300
        ? 200
        : req.status >= 400 && req.status < 500
          ? 400
          : 500;
    statusCounts[statusGroup] = (statusCounts[statusGroup] || 0) + 1;
  });

  const data = Object.entries(statusCounts).map(([status, count]) => ({
    value: count,
    name:
      status === "200"
        ? CHART_LABELS.STATUS_2XX_SUCCESS
        : status === "400"
          ? CHART_LABELS.STATUS_4XX_CLIENT_ERROR
          : CHART_LABELS.STATUS_5XX_SERVER_ERROR,
  }));

  return {
    tooltip: {
      trigger: "item",
      formatter: CHART_TOOLTIPS.STATUS_FORMAT,
    },
    series: [
      {
        type: "pie",
        radius: ["40%", "70%"],
        data,
        itemStyle: {
          color: (params: any) => {
            if (params.name.includes("2xx")) return "#0ec9c2";
            if (params.name.includes("4xx")) return "#f59e0b";
            return "#ef4444";
          },
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: "rgba(0, 0, 0, 0.5)",
          },
        },
      },
    ],
  };
}

export function createDurationOption(networkRequests: NetworkRequest[]) {
  const requests = networkRequests.map((req) => ({
    name: req.url.split("/").pop() || req.url.substring(0, 20),
    duration: req.duration,
    status: req.status,
  }));

  return {
    tooltip: {
      trigger: "axis",
      formatter: (params: any) => {
        const param = params[0];
        return CHART_TOOLTIPS.DURATION_FORMAT.replace(
          "{name}",
          param.name,
        ).replace("{value}", param.value.toString());
      },
    },
    xAxis: {
      type: "category",
      data: requests.map((r) => r.name),
      axisLabel: {
        rotate: requests.length > 4 ? 25 : 0,
        fontSize: 11,
      },
    },
    yAxis: {
      type: "value",
      name: CHART_LABELS.DURATION_MS,
      nameTextStyle: {
        padding: [0, 0, 0, 20],
      },
    },
    series: [
      {
        name: CHART_LABELS.DURATION,
        type: "bar",
        data: requests.map((r) => r.duration),
        itemStyle: {
          color: (params: any) => {
            const req = requests[params.dataIndex];
            if (req.duration > 1000) return "#ef4444";
            if (req.duration > 500) return "#f59e0b";
            return "#0ec9c2";
          },
        },
        barMaxWidth: 60,
      },
    ],
  };
}
