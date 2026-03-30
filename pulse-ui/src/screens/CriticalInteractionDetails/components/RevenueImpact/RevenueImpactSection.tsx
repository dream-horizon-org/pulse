import { Text } from "@mantine/core";
import { IconCurrencyDollar, IconAlertTriangle } from "@tabler/icons-react";
import classes from "./RevenueImpactSection.module.css";
import { AreaChart } from "../../../../components/Charts/AreaChart";
import {
  MOCK_REVENUE_METRICS,
  generateRevenueTrend,
} from "../../../../mocks/revenueData";
import dayjs from "dayjs";
import { useMemo } from "react";

const formatCurrency = (value: number, currency = "USD") =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(value);

type RevenueImpactSectionProps = {
  startTime: string;
  endTime: string;
};

export function RevenueImpactSection({ startTime, endTime }: RevenueImpactSectionProps) {
  const { currency } = MOCK_REVENUE_METRICS;

  const trendData = useMemo(
    () => generateRevenueTrend(startTime, endTime),
    [startTime, endTime],
  );

  const { revenueGenerated, revenueAtRisk, percentageAtRisk } = useMemo(() => {
    const generated = trendData.reduce((sum, d) => sum + d.generated, 0);
    const atRisk = trendData.reduce((sum, d) => sum + d.atRisk, 0);
    const total = generated + atRisk;
    return {
      revenueGenerated: generated,
      revenueAtRisk: atRisk,
      percentageAtRisk: total > 0 ? ((atRisk / total) * 100).toFixed(1) : "0",
    };
  }, [trendData]);

  const dateRangeLabel = useMemo(() => {
    const start = dayjs(startTime);
    const end = dayjs(endTime);
    const diffDays = end.diff(start, "day");
    const diffHours = end.diff(start, "hour");
    if (diffDays >= 1) return `Last ${diffDays} day${diffDays !== 1 ? "s" : ""}`;
    return `Last ${diffHours} hour${diffHours !== 1 ? "s" : ""}`;
  }, [startTime, endTime]);

  const chartOption = {
    grid: { left: 60, right: 24, top: 16, bottom: 40 },
    tooltip: {
      trigger: "axis",
      formatter: (params: { name: string; value: [number, number]; seriesName: string }[]) => {
        if (!params?.length) return "";
        const time = dayjs(params[0].value[0]).format("MMM DD, HH:mm");
        const lines = params
          .map(
            (p) =>
              `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${
                p.seriesName === "Revenue Generated" ? "#10b981" : "#ef4444"
              };margin-right:6px;"></span>${p.seriesName}: <b>${formatCurrency(
                p.value[1],
                currency,
              )}</b>`,
          )
          .join("<br/>");
        return `<div style="font-size:12px"><b>${time}</b><br/>${lines}</div>`;
      },
    },
    xAxis: {
      type: "time",
      axisLabel: {
        formatter: (value: number) => dayjs(value).format("MMM DD"),
        fontSize: 10,
      },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      axisLabel: {
        formatter: (value: number) =>
          value >= 1000 ? `$${(value / 1000).toFixed(0)}k` : `$${value}`,
        fontSize: 10,
      },
      splitLine: { lineStyle: { color: "rgba(14, 201, 194, 0.08)" } },
    },
    series: [
      {
        name: "Revenue Generated",
        type: "line",
        smooth: true,
        color: "#10b981",
        areaStyle: { color: "rgba(16, 185, 129, 0.12)" },
        data: trendData.map((d) => [d.timestamp, d.generated]),
        showSymbol: false,
      },
      {
        name: "Revenue at Risk",
        type: "line",
        smooth: true,
        color: "#ef4444",
        areaStyle: { color: "rgba(239, 68, 68, 0.1)" },
        data: trendData.map((d) => [d.timestamp, d.atRisk]),
        showSymbol: false,
      },
    ],
  };

  return (
    <div className={classes.container}>
      <div className={classes.header}>
        <IconCurrencyDollar size={16} className={classes.headerIcon} />
        <Text className={classes.title}>Revenue Impact</Text>
      </div>

      <div className={classes.metricsRow}>
        <div className={`${classes.metricCard} ${classes.metricCardGenerated}`}>
          <Text className={classes.metricLabel}>Revenue Generated</Text>
          <Text className={`${classes.metricValue} ${classes.metricValueGenerated}`}>
            {formatCurrency(revenueGenerated, currency)}
          </Text>
          <Text className={classes.metricDescription}>
            from successful interactions ({dateRangeLabel.toLowerCase()})
          </Text>
        </div>

        <div className={`${classes.metricCard} ${classes.metricCardAtRisk}`}>
          <Text className={classes.metricLabel}>Revenue at Risk</Text>
          <Text className={`${classes.metricValue} ${classes.metricValueAtRisk}`}>
            {formatCurrency(revenueAtRisk, currency)}
          </Text>
          <div>
            <span className={`${classes.metricBadge} ${classes.metricBadgeAtRisk}`}>
              <IconAlertTriangle size={9} />
              {percentageAtRisk}% of total
            </span>
          </div>
          <Text className={classes.metricDescription}>
            lost due to interaction failures
          </Text>
        </div>
      </div>

      <Text className={classes.chartLabel}>Revenue Trend — {dateRangeLabel}</Text>
      <AreaChart
        height={180}
        withLegend
        syncTooltips={false}
        option={chartOption}
      />
    </div>
  );
}
