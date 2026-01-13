import React, { useMemo } from "react";
import { Box, Text } from "@mantine/core";
import { IconChartDonut, IconChartPie } from "@tabler/icons-react";
import { useGetDataQuery } from "../../../../hooks/useGetDataQuery";
import {
  PieChart,
  CustomToolTip,
  createTooltipFormatter,
} from "../../../../components/Charts";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { ChartSkeleton } from "../../../../components/Skeletons";
import {
  StatusCodeDistributionProps,
  StatusCodeCategory,
} from "./StatusCodeDistribution.interface";
import classes from "./StatusCodeDistribution.module.css";

// Color palette for status code categories
const STATUS_CODE_COLORS: Record<string, string> = {
  "2xx": "#22c55e", // Green - Success
  "3xx": "#3b82f6", // Blue - Redirect
  "4xx": "#f59e0b", // Orange - Client Error
  "5xx": "#ef4444", // Red - Server Error
  "0xx": "#8b5cf6", // Purple - Connection/Timeout Error
};

const STATUS_CODE_LABELS: Record<string, string> = {
  "2xx": "Success (2xx)",
  "3xx": "Redirect (3xx)",
  "4xx": "Client Error (4xx)",
  "5xx": "Server Error (5xx)",
  "0xx": "Connection Error",
};

export const StatusCodeDistribution: React.FC<StatusCodeDistributionProps> = ({
  url,
  startTime,
  endTime,
  additionalFilters = [],
}) => {
  // Query for all network requests grouped by status code category
  const { data, isLoading, error } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: { start: startTime, end: endTime },
      select: [
        {
          function: "CUSTOM" as const,
          param: {
            expression: "count()",
          },
          alias: "request_count",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "SpanAttributes['http.status_code']",
          },
          alias: "status_code",
        },
      ],
      groupBy: ["status_code"],
      filters: [
        {
          field: "PulseType",
          operator: "LIKE" as const,
          value: ["%network%"],
        },
        {
          field: "SpanAttributes['http.url']",
          operator: "EQ" as const,
          value: [url],
        },
        ...additionalFilters,
      ],
      orderBy: [
        {
          field: "request_count",
          direction: "DESC" as const,
        },
      ],
    },
    enabled: !!url && !!startTime && !!endTime,
  });

  // Transform and categorize status codes
  const { categories, totalRequests } = useMemo(() => {
    if (!data?.data?.rows || data.data.rows.length === 0) {
      return { categories: [], totalRequests: 0 };
    }

    const fields = data.data.fields;
    const countIndex = fields.indexOf("request_count");
    const statusCodeIndex = fields.indexOf("status_code");

    // Aggregate by category
    const categoryMap: Record<
      string,
      { count: number; statusCodes: { code: string; count: number }[] }
    > = {};

    let total = 0;

    data.data.rows.forEach((row) => {
      const statusCode = String(row[statusCodeIndex] || "0");
      const count = parseFloat(String(row[countIndex])) || 0;
      total += count;

      // Determine category based on status code
      let category: string;
      const statusNum = parseInt(statusCode, 10);

      if (isNaN(statusNum) || statusNum === 0) {
        category = "0xx"; // Connection/Timeout errors
      } else if (statusNum >= 200 && statusNum < 300) {
        category = "2xx";
      } else if (statusNum >= 300 && statusNum < 400) {
        category = "3xx";
      } else if (statusNum >= 400 && statusNum < 500) {
        category = "4xx";
      } else if (statusNum >= 500 && statusNum < 600) {
        category = "5xx";
      } else {
        category = "0xx"; // Unknown
      }

      if (!categoryMap[category]) {
        categoryMap[category] = { count: 0, statusCodes: [] };
      }
      categoryMap[category].count += count;
      categoryMap[category].statusCodes.push({ code: statusCode, count });
    });

    // Convert to array and calculate percentages
    const categoriesArray: StatusCodeCategory[] = Object.entries(categoryMap)
      .map(([category, data]) => ({
        category,
        label: STATUS_CODE_LABELS[category] || category,
        count: Math.round(data.count),
        percentage:
          total > 0 ? Math.round((data.count / total) * 1000) / 10 : 0,
        color: STATUS_CODE_COLORS[category] || "#6c757d",
        statusCodes: data.statusCodes
          .map((sc) => ({
            statusCode: sc.code,
            count: Math.round(sc.count),
            percentage:
              total > 0 ? Math.round((sc.count / total) * 1000) / 10 : 0,
            color: STATUS_CODE_COLORS[category] || "#6c757d",
          }))
          .sort((a, b) => b.count - a.count),
      }))
      .sort((a, b) => b.count - a.count);

    return { categories: categoriesArray, totalRequests: Math.round(total) };
  }, [data]);

  // ECharts pie chart options
  const chartOption = useMemo(() => {
    if (categories.length === 0) return {};

    return {
      tooltip: {
        ...CustomToolTip,
        trigger: "item",
        confine: true,
        formatter: createTooltipFormatter({
          valueFormatter: (value: number) => value.toLocaleString(),
        }),
      },
      legend: {
        show: false, // We'll use custom legend
      },
      series: [
        {
          name: "Status Code Distribution",
          type: "pie",
          radius: ["55%", "85%"],
          center: ["50%", "50%"],
          avoidLabelOverlap: true,
          itemStyle: {
            borderRadius: 4,
            borderColor: "#fff",
            borderWidth: 2,
          },
          label: {
            show: true,
            position: "inside",
            formatter: (params: { percent: number }) =>
              params.percent >= 5 ? `${Math.round(params.percent)}%` : "",
            fontWeight: 600,
            fontSize: 11,
            color: "#fff",
          },
          labelLine: {
            show: false,
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: "rgba(0, 0, 0, 0.2)",
            },
            label: {
              show: true,
              fontWeight: 700,
            },
          },
          data: categories.map((cat) => ({
            value: cat.count,
            name: cat.label,
            itemStyle: { color: cat.color },
          })),
        },
      ],
    };
  }, [categories]);

  if (error || data?.error) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconChartPie size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Status Code Distribution</Text>
            <Text className={classes.description}>
              HTTP response breakdown by status category
            </Text>
          </Box>
        </Box>
        <ErrorAndEmptyState message="Failed to load status code distribution. Please try again." />
      </Box>
    );
  }

  if (isLoading) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconChartPie size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Status Code Distribution</Text>
            <Text className={classes.description}>
              HTTP response breakdown by status category
            </Text>
          </Box>
        </Box>
        <ChartSkeleton height={220} />
      </Box>
    );
  }

  if (categories.length === 0) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconChartPie size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Status Code Distribution</Text>
            <Text className={classes.description}>
              HTTP response breakdown by status category
            </Text>
          </Box>
        </Box>
        <Box className={classes.emptyState}>
          <IconChartDonut size={32} className={classes.emptyIcon} />
          <Text className={classes.emptyMessage}>
            No request data available for this time range
          </Text>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      <Box className={classes.header}>
        <Box className={classes.headerIcon}>
          <IconChartPie size={18} />
        </Box>
        <Box className={classes.headerContent}>
          <Text className={classes.title}>Status Code Distribution</Text>
          <Text className={classes.description}>
            HTTP response breakdown by status category
          </Text>
        </Box>
      </Box>

      <Box className={classes.chartContainer}>
        {/* Donut Chart */}
        <Box className={classes.donutWrapper}>
          <PieChart height={220} option={chartOption} withLegend={false} />
          <Box className={classes.centerLabel}>
            <Text className={classes.totalCount}>
              {totalRequests.toLocaleString()}
            </Text>
            <Text className={classes.totalLabel}>Total</Text>
          </Box>
        </Box>

        {/* Custom Legend with counts and percentages */}
        <Box className={classes.legendContainer}>
          {categories.map((category) => (
            <Box key={category.category} className={classes.legendItem}>
              <Box className={classes.legendLeft}>
                <Box
                  className={classes.legendIndicator}
                  style={{ backgroundColor: category.color }}
                />
                <Text className={classes.legendLabel}>{category.label}</Text>
              </Box>
              <Box className={classes.legendRight}>
                <Text
                  className={classes.legendCount}
                  style={{ color: category.color }}
                >
                  {category.count.toLocaleString()}
                </Text>
                <Text className={classes.legendPercentage}>
                  {category.percentage}%
                </Text>
              </Box>
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
};

