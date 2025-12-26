import { SegmentedControl, TagsInput, Text, TextInput } from "@mantine/core";
import { useMemo, useState } from "react";
import {
  BarChart,
  createTooltipFormatter,
} from "../../../../components/Charts";
import classes from "./EngagementBreakdown.module.css";
import {
  BreakdownDimension,
  EngagementBreakdownProps,
  NonCustomDimension,
} from "./EngagementBreakdown.interface";
import { useGetDataQuery } from "../../../../hooks";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { ChartSkeleton, TableSkeleton } from "../../../../components/Skeletons";
import { ErrorAndEmptyStateWithNotification } from "../../../CriticalInteractionDetails/components/InteractionDetailsMainContent/components/ErrorAndEmptyStateWithNotification";
import { PulseType } from "../../../../constants/PulseOtelSemcov";

dayjs.extend(utc);

const dimensionOptions: Array<{ label: string; value: BreakdownDimension }> = [
  { label: "Regions", value: "region" },
  { label: "Networks", value: "network" },
  { label: "Platforms", value: "platform" },
  { label: "OS", value: "os" },
  { label: "Device", value: "device" },
  { label: "Custom attributes", value: "custom" },
];

export function EngagementBreakdown({
  customAttributeData: _customAttributeData,
  customAttributeOptions: _customAttributeOptions,
}: EngagementBreakdownProps) {
  const [dimension, setDimension] = useState<BreakdownDimension>("region");
  const [customAttributeName, setCustomAttributeName] = useState<string>("");
  const [attributeValues, setAttributeValues] = useState<string[]>([]);

  // Calculate date ranges
  const {
    dailyStartDate,
    dailyEndDate,
    weeklyStartDate,
    weeklyEndDate,
    monthlyStartDate,
    monthlyEndDate,
  } = useMemo(() => {
    const now = dayjs().utc().startOf("day");
    return {
      dailyStartDate: now.subtract(1, "day").toISOString(),
      dailyEndDate: now.toISOString(),
      weeklyStartDate: now.subtract(6, "days").startOf("day").toISOString(),
      weeklyEndDate: now.endOf("day").toISOString(),
      monthlyStartDate: now.subtract(27, "days").startOf("day").toISOString(),
      monthlyEndDate: now.endOf("day").toISOString(),
    };
  }, []);

  // Get field mapping for dimension
  const dimensionFieldMap = useMemo(() => {
    const map: Record<NonCustomDimension, { field: string; alias: string }> = {
      region: { field: "GeoState", alias: "region" },
      network: { field: "NetworkProvider", alias: "network_provider" },
      platform: { field: "Platform", alias: "platform" },
      os: { field: "OsVersion", alias: "osVersion" },
      device: { field: "DeviceModel", alias: "deviceModel" },
    };
    return map;
  }, []);

  // Build request body based on dimension
  const requestBody = useMemo(() => {
    if (dimension === "custom") {
      // For custom attributes, use LogAttributes['attributeName'] format
      if (!customAttributeName.trim() || attributeValues.length === 0) {
        return null;
      }

      const attributeField = `LogAttributes['pulse.user.${customAttributeName}']`;
      const attributeAlias = customAttributeName;

      return {
        dataType: "LOGS" as const,
        timeRange: {
          start: monthlyStartDate,
          end: monthlyEndDate,
        },
        select: [
          {
            function: "COL" as const,
            param: {
              field: attributeField,
            },
            alias: attributeAlias,
          },
          {
            function: "CUSTOM" as const,
            param: { expression: "uniqCombined(UserId)" },
            alias: "user_count",
          },
          {
            function: "CUSTOM" as const,
            param: { expression: "uniqCombined(SessionId)" },
            alias: "session_count",
          },
        ],
        filters: [
          { field: "PulseType", operator: "EQ" as const, value: [PulseType.SESSION_START] },
          {
            field: attributeField,
            operator: "IN" as const,
            value: attributeValues,
          },
        ],
        groupBy: [attributeAlias],
      };
    }

    const fieldConfig = dimensionFieldMap[dimension as NonCustomDimension];
    if (!fieldConfig) return null;

    return {
      dataType: "LOGS" as const,
      timeRange: {
        start: monthlyStartDate,
        end: monthlyEndDate,
      },
      select: [
        {
          function: "COL" as const,
          param: { field: fieldConfig.field },
          alias: fieldConfig.alias,
        },
        {
          function: "CUSTOM" as const,
          param: { expression: "uniqCombined(UserId)" },
          alias: "user_count",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: "uniqCombined(SessionId)" },
          alias: "session_count",
        },
      ],
      filters: [
        { field: "PulseType", operator: "EQ" as const, value: [PulseType.SESSION_START] },
      ],
      groupBy: [fieldConfig.alias],
    };
  }, [
    dimension,
    dimensionFieldMap,
    monthlyStartDate,
    monthlyEndDate,
    customAttributeName,
    attributeValues,
  ]);

  // Fetch DAU (last 1 day)
  const { data: dauData, isLoading: isLoadingDau } = useGetDataQuery({
    requestBody: requestBody
      ? {
          ...requestBody,
          timeRange: {
            start: dailyStartDate,
            end: dailyEndDate,
          },
        }
      : {
          dataType: "LOGS" as const,
          timeRange: {
            start: dailyStartDate,
            end: dailyEndDate,
          },
          select: [],
        },
    enabled: !!requestBody,
  });

  // Fetch WAU (last 7 days)
  const { data: wauData, isLoading: isLoadingWau } = useGetDataQuery({
    requestBody: requestBody
      ? {
          ...requestBody,
          timeRange: {
            start: weeklyStartDate,
            end: weeklyEndDate,
          },
        }
      : {
          dataType: "LOGS" as const,
          timeRange: {
            start: weeklyStartDate,
            end: weeklyEndDate,
          },
          select: [],
        },
    enabled: !!requestBody,
  });

  // Fetch MAU and Sessions (last 30 days)
  const { data: mauData, isLoading: isLoadingMau } = useGetDataQuery({
    requestBody: requestBody || {
      dataType: "LOGS" as const,
      timeRange: {
        start: monthlyStartDate,
        end: monthlyEndDate,
      },
      select: [],
    },
    enabled: !!requestBody,
  });

  // Transform API data to BreakdownItem format
  const transformedData = useMemo(() => {
    if (!dauData?.data || !wauData?.data || !mauData?.data) {
      return [];
    }

    const segmentAlias =
      dimension === "custom"
        ? customAttributeName
        : dimensionFieldMap[dimension as NonCustomDimension]?.alias ||
          "segment_name";
    const segmentNameIndex = mauData.data.fields.indexOf(segmentAlias);
    const userCountIndex = mauData.data.fields.indexOf("user_count");
    const sessionCountIndex = mauData.data.fields.indexOf("session_count");

    // Helper to normalize empty segment names to "Unknown"
    const normalizeSegmentName = (value: unknown): string => {
      const segment = String(value || "").trim();
      return segment === "" ? "Unknown" : segment;
    };

    // Get DAU data
    const dauMap = new Map<string, number>();
    if (dauData.data.rows) {
      const dauSegmentIndex = dauData.data.fields.indexOf(segmentAlias);
      const dauUserIndex = dauData.data.fields.indexOf("user_count");
      dauData.data.rows.forEach((row) => {
        const segment = normalizeSegmentName(row[dauSegmentIndex]);
        const users = parseFloat(row[dauUserIndex]) || 0;
        // Accumulate values for the same segment (e.g., multiple empty values -> "Unknown")
        dauMap.set(segment, (dauMap.get(segment) || 0) + users);
      });
    }

    // Get WAU data
    const wauMap = new Map<string, number>();
    if (wauData.data.rows) {
      const wauSegmentIndex = wauData.data.fields.indexOf(segmentAlias);
      const wauUserIndex = wauData.data.fields.indexOf("user_count");
      wauData.data.rows.forEach((row) => {
        const segment = normalizeSegmentName(row[wauSegmentIndex]);
        const users = parseFloat(row[wauUserIndex]) || 0;
        wauMap.set(segment, (wauMap.get(segment) || 0) + users);
      });
    }

    // Get MAU and Sessions data - use a map to accumulate values for same segment
    const resultMap = new Map<
      string,
      {
        name: string;
        dau: number;
        wau: number;
        mau: number;
        sessions: number;
        wowChange: number;
      }
    >();

    if (mauData.data.rows) {
      mauData.data.rows.forEach((row) => {
        const segment = normalizeSegmentName(row[segmentNameIndex]);
        const mau = parseFloat(row[userCountIndex]) || 0;
        const sessions = parseFloat(row[sessionCountIndex]) || 0;

        const existing = resultMap.get(segment);
        if (existing) {
          existing.mau += mau;
          existing.sessions += sessions;
        } else {
          resultMap.set(segment, {
            name: segment,
            dau: 0,
            wau: 0,
            mau,
            sessions,
            wowChange: 0,
          });
        }
      });
    }

    // Merge DAU and WAU data into results
    const result = Array.from(resultMap.values()).map((item) => ({
      ...item,
      dau: Math.round(dauMap.get(item.name) || 0),
      wau: Math.round(wauMap.get(item.name) || 0),
      mau: Math.round(item.mau),
      sessions: Math.round(item.sessions),
    }));

    return result.sort((a, b) => b.mau - a.mau);
  }, [
    dimension,
    dimensionFieldMap,
    dauData,
    wauData,
    mauData,
    customAttributeName,
  ]);

  const chartItems = useMemo(() => {
    return transformedData;
  }, [transformedData]);

  const isLoading = isLoadingDau || isLoadingWau || isLoadingMau;
  const hasError = dauData?.error || wauData?.error || mauData?.error;

  const totals = useMemo(
    () =>
      chartItems.reduce(
        (acc, item) => {
          acc.dau += item.dau;
          acc.wau += item.wau;
          acc.mau += item.mau;
          acc.sessions += item.sessions;
          return acc;
        },
        { dau: 0, wau: 0, mau: 0, sessions: 0 },
      ),
    [chartItems],
  );

  const barChartOption = useMemo(
    () => ({
      color: ["#0ec9c2", "#0ba09a", "#2c3e50", "#a855f7"],
      tooltip: {
        trigger: "axis",
        formatter: createTooltipFormatter({
          valueFormatter: (value: any) => {
            const numericValue = Array.isArray(value) ? value[1] : value;
            return `${parseFloat(numericValue).toFixed(0)}`;
          },
        }),
      },
      xAxis: {
        type: "category",
        data: chartItems.map((item) => item.name),
        axisLabel: {
          interval: 0,
          rotate: chartItems.length > 4 ? 20 : 0,
        },
      },
      yAxis: {
        type: "value",
        axisLabel: {
          formatter: (value: number) =>
            value >= 1000 ? `${(value / 1000).toFixed(0)}K` : `${value}`,
        },
      },
      series: [
        {
          name: "DAU",
          type: "bar",
          barWidth: 14,
          data: chartItems.map((item) => item.dau),
        },
        {
          name: "WAU",
          type: "bar",
          barWidth: 14,
          data: chartItems.map((item) => item.wau),
        },
        {
          name: "MAU",
          type: "bar",
          barWidth: 14,
          data: chartItems.map((item) => item.mau),
        },
        {
          name: "Sessions",
          type: "bar",
          barWidth: 14,
          data: chartItems.map((item) => item.sessions),
        },
      ],
    }),
    [chartItems],
  );

  const subtitle =
    dimension === "custom"
      ? "Slice engagement metrics by any user-defined attribute."
      : "Dive deeper into how each cohort contributes to DAU/WAU/MAU and sessions.";

  const hasData = chartItems.length > 0;

  return (
    <div className={classes.card}>
      <div className={classes.headerRow}>
        <div>
          <h3 className={classes.title}>Detailed engagement analysis</h3>
          <p className={classes.subtitle}>{subtitle}</p>
        </div>
        <div className={classes.controls}>
          <SegmentedControl
            size="xs"
            value={dimension}
            onChange={(value) => setDimension(value as BreakdownDimension)}
            data={dimensionOptions}
          />
        </div>
      </div>

      {dimension === "custom" && (
        <div className={classes.customControls}>
          <TextInput
            label="Custom attribute"
            placeholder="Enter attribute name (e.g., VIP tier, Subscription status)"
            value={customAttributeName}
            onChange={(event) =>
              setCustomAttributeName(event.currentTarget.value)
            }
          />
          <TagsInput
            label="Attribute values"
            placeholder="Enter values and press Enter"
            value={attributeValues}
            onChange={setAttributeValues}
            splitChars={[","]}
          />
        </div>
      )}

      <Text size="xs" c="dimmed">
        {hasData
          ? `${chartItems.length} segments • ${totals.sessions.toLocaleString()} sessions • ${totals.mau.toLocaleString()} MAU`
          : "Select at least one value to visualise engagement."}
      </Text>

      {isLoading ? (
        <div className={classes.skeletonContainer}>
          <ChartSkeleton height={360} showLegend />
          <TableSkeleton columns={5} rows={5} />
        </div>
      ) : hasError ? (
        <ErrorAndEmptyStateWithNotification
          message="Failed to load engagement data"
          errorDetails={
            dauData?.error?.message ||
            wauData?.error?.message ||
            mauData?.error?.message ||
            "Unknown error"
          }
        />
      ) : hasData ? (
        <>
          <div className={classes.chartWrapper}>
            <BarChart height={360} option={barChartOption} />
          </div>
          <div className={classes.tableWrapper}>
            <table className={classes.breakdownTable}>
              <thead>
                <tr>
                  <th>Segment</th>
                  <th>DAU</th>
                  <th>WAU</th>
                  <th>MAU</th>
                  <th>Sessions</th>
                  {/* <th>WoW change</th> */}
                </tr>
              </thead>
              <tbody>
                {chartItems.map((item) => (
                  <tr key={item.name}>
                    <td>
                      <div className={classes.segmentCell}>
                        <span className={classes.segmentName}>{item.name}</span>
                        <span className={classes.segmentHelper}>
                          {dimension === "custom"
                            ? "Custom attribute"
                            : `By ${dimension}`}
                        </span>
                      </div>
                    </td>
                    <td>{item.dau.toLocaleString()}</td>
                    <td>{item.wau.toLocaleString()}</td>
                    <td>{item.mau.toLocaleString()}</td>
                    <td>{item.sessions.toLocaleString()}</td>
                    {/* <td
                      className={
                        item.wowChange >= 0
                          ? classes.trendPositive
                          : classes.trendNegative
                      }
                    >
                      {item.wowChange >= 0 ? "+" : ""}
                      {item.wowChange.toFixed(1)}%
                    </td> */}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : (
        <div className={classes.emptyState}>
          No datapoints for the current selection. Adjust your custom attribute
          filters to render the graph.
        </div>
      )}
    </div>
  );
}
