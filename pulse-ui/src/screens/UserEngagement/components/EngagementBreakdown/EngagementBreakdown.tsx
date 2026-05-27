import {
  Group,
  SegmentedControl,
  TagsInput,
  Text,
  TextInput,
  Tooltip,
} from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
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
import { useGetDataQuery, getDataQueryStatus } from "../../../../hooks";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  ChartSkeleton,
  SkeletonLoader,
  TableSkeleton,
} from "../../../../components/Skeletons";
import { COLUMN_NAME, PulseType } from "../../../../constants/PulseOtelSemcov";
import { getRegionName } from "../../utils/region";

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
      monthlyStartDate: now.subtract(29, "days").startOf("day").toISOString(),
      monthlyEndDate: now.endOf("day").toISOString(),
    };
  }, []);

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

  const requestBody = useMemo(() => {
    if (dimension === "custom") {
      if (!customAttributeName.trim() || attributeValues.length === 0) {
        return null;
      }

      const attributeField = `LogAttributes['pulse.user.${customAttributeName}']`;
      const attributeAlias = customAttributeName;

      return {
        dataType: "TRACES" as const,
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
            param: {
              expression: `uniq(nullIf(${COLUMN_NAME.INSTALLATION_ID}, ''))`,
            },
            alias: "user_count",
          },
          {
            function: "CUSTOM" as const,
            param: { expression: "uniq(nullIf(SessionId, ''))" },
            alias: "session_count",
          },
        ],
        filters: [
          {
            field: "PulseType",
            operator: "EQ" as const,
            value: [PulseType.APP_START],
          },
          {
            field: attributeField,
            operator: "IN" as const,
            value: attributeValues,
          },
        ],
        groupBy: [attributeAlias],
        limit: 10,
      };
    }

    const fieldConfig = dimensionFieldMap[dimension as NonCustomDimension];
    if (!fieldConfig) return null;

    const select = [
      {
        function: "COL" as const,
        param: { field: fieldConfig.field },
        alias: fieldConfig.alias,
      },
      {
        function: "CUSTOM" as const,
        param: {
          expression: `uniq(nullIf(${COLUMN_NAME.INSTALLATION_ID}, ''))`,
        },
        alias: "user_count",
      },
      {
        function: "CUSTOM" as const,
        param: { expression: "uniq(nullIf(SessionId, ''))" },
        alias: "session_count",
      },
    ];

    if (dimension === "region") {
      select.push({
        function: "CUSTOM" as const,
        param: {
          expression: `any(nullIf(${COLUMN_NAME.COUNTRY}, ''))`,
        },
        alias: "country_code",
      });
    }

    return {
      dataType: "TRACES" as const,
      timeRange: {
        start: monthlyStartDate,
        end: monthlyEndDate,
      },
      select,
      filters: [
        {
          field: "PulseType",
          operator: "EQ" as const,
          value: [PulseType.APP_START],
        },
      ],
      groupBy: [fieldConfig.alias],
      limit: 10,
    };
  }, [
    dimension,
    dimensionFieldMap,
    monthlyStartDate,
    monthlyEndDate,
    customAttributeName,
    attributeValues,
  ]);

  const dauQuery = useGetDataQuery({
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

  const wauQuery = useGetDataQuery({
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

  const mauQuery = useGetDataQuery({
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

  const dauData = dauQuery.data;
  const wauData = wauQuery.data;
  const mauData = mauQuery.data;

  const dauStatus = getDataQueryStatus(dauQuery);
  const wauStatus = getDataQueryStatus(wauQuery);
  const mauStatus = getDataQueryStatus(mauQuery);

  const transformedData = useMemo(() => {
    const segmentAlias =
      dimension === "custom"
        ? customAttributeName
        : dimensionFieldMap[dimension as NonCustomDimension]?.alias ||
          "segment_name";

    const countryCodeIndex =
      dimension === "region"
        ? (mauData?.data?.fields.indexOf("country_code") ??
          dauData?.data?.fields.indexOf("country_code") ??
          wauData?.data?.fields.indexOf("country_code") ??
          -1)
        : -1;

    const normalizeSegmentName = (
      value: unknown,
      countryCode?: unknown,
    ): string => {
      const segment = String(value || "").trim();
      if (segmentAlias === "region") {
        return getRegionName(segment, String(countryCode || ""));
      }
      return segment === "" ? "Unknown" : segment;
    };

    const buildUserMap = (
      responseData: NonNullable<typeof dauData>["data"] | undefined,
    ) => {
      const map = new Map<string, number>();
      if (!responseData?.rows?.length) return map;

      const segmentIndex = responseData.fields.indexOf(segmentAlias);
      const userIndex = responseData.fields.indexOf("user_count");

      responseData.rows.forEach((row) => {
        const segment = normalizeSegmentName(
          row[segmentIndex],
          row[countryCodeIndex],
        );
        const users = parseFloat(row[userIndex]) || 0;
        map.set(segment, (map.get(segment) || 0) + users);
      });

      return map;
    };

    const mauSessionMap = new Map<string, { mau: number; sessions: number }>();
    if (mauData?.data?.rows?.length) {
      const segmentIndex = mauData.data.fields.indexOf(segmentAlias);
      const userCountIndex = mauData.data.fields.indexOf("user_count");
      const sessionCountIndex = mauData.data.fields.indexOf("session_count");

      mauData.data.rows.forEach((row) => {
        const segment = normalizeSegmentName(
          row[segmentIndex],
          row[countryCodeIndex],
        );
        const mau = parseFloat(row[userCountIndex]) || 0;
        const sessions = parseFloat(row[sessionCountIndex]) || 0;

        const existing = mauSessionMap.get(segment);
        if (existing) {
          existing.mau += mau;
          existing.sessions += sessions;
        } else {
          mauSessionMap.set(segment, { mau, sessions });
        }
      });
    }

    const dauMap = buildUserMap(dauData?.data);
    const wauMap = buildUserMap(wauData?.data);

    const segmentNames = new Set<string>([
      ...Array.from(dauMap.keys()),
      ...Array.from(wauMap.keys()),
      ...Array.from(mauSessionMap.keys()),
    ]);

    if (segmentNames.size === 0) return [];

    return Array.from(segmentNames)
      .map((name) => ({
        name,
        dau: dauMap.has(name) ? Math.round(dauMap.get(name)!) : null,
        wau: wauMap.has(name) ? Math.round(wauMap.get(name)!) : null,
        mau: mauSessionMap.has(name)
          ? Math.round(mauSessionMap.get(name)!.mau)
          : null,
        sessions: mauSessionMap.has(name)
          ? Math.round(mauSessionMap.get(name)!.sessions)
          : null,
        wowChange: 0,
      }))
      .sort((a, b) => {
        const aSort = a.mau ?? a.wau ?? a.dau ?? 0;
        const bSort = b.mau ?? b.wau ?? b.dau ?? 0;
        return bSort - aSort;
      })
      .slice(0, 10);
  }, [
    dimension,
    dimensionFieldMap,
    dauData,
    wauData,
    mauData,
    customAttributeName,
  ]);

  const chartItems = transformedData;
  const isAnyLoading =
    dauStatus.loading || wauStatus.loading || mauStatus.loading;
  const hasData = chartItems.length > 0;
  const showFullSkeleton = !!requestBody && !hasData && isAnyLoading;

  const totals = useMemo(
    () =>
      chartItems.reduce(
        (acc, item) => {
          acc.dau += item.dau ?? 0;
          acc.wau += item.wau ?? 0;
          acc.mau += item.mau ?? 0;
          acc.sessions += item.sessions ?? 0;
          return acc;
        },
        { dau: 0, wau: 0, mau: 0, sessions: 0 },
      ),
    [chartItems],
  );

  const barChartOption = useMemo(() => {
    const series = [];

    if (!dauStatus.loading && !dauStatus.failed && dauData?.data) {
      series.push({
        name: "DAU",
        type: "bar" as const,
        barWidth: 14,
        data: chartItems.map((item) => item.dau ?? 0),
      });
    }

    if (!wauStatus.loading && !wauStatus.failed && wauData?.data) {
      series.push({
        name: "WAU",
        type: "bar" as const,
        barWidth: 14,
        data: chartItems.map((item) => item.wau ?? 0),
      });
    }

    if (!mauStatus.loading && !mauStatus.failed && mauData?.data) {
      series.push(
        {
          name: "MAU",
          type: "bar" as const,
          barWidth: 14,
          data: chartItems.map((item) => item.mau ?? 0),
        },
        {
          name: "Sessions",
          type: "bar" as const,
          barWidth: 14,
          data: chartItems.map((item) => item.sessions ?? 0),
        },
      );
    }

    return {
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
      series,
    };
  }, [chartItems, dauData, wauData, mauData, dauStatus, wauStatus, mauStatus]);

  const hasChartSeries = barChartOption.series.length > 0;

  const subtitle =
    dimension === "custom"
      ? "Slice engagement metrics by any user-defined attribute."
      : "Dive deeper into how top 10 cohorts contributes to DAU/WAU/MAU and sessions.";

  const renderMetricCell = (
    value: number | null,
    isLoading: boolean,
    isFailed: boolean,
  ) => {
    if (isLoading) {
      return <SkeletonLoader height={14} width="50%" radius="sm" />;
    }

    if (isFailed) {
      return (
        <Text size="sm" c="dimmed">
          N/A
        </Text>
      );
    }

    return (value ?? 0).toLocaleString();
  };

  const renderSummaryLine = () => {
    if (!hasData) {
      return "Select at least one value to visualise engagement.";
    }

    const sessionsText = mauStatus.loading
      ? "…"
      : mauStatus.failed
        ? "N/A"
        : totals.sessions.toLocaleString();
    const mauText = mauStatus.loading
      ? "…"
      : mauStatus.failed
        ? "N/A"
        : totals.mau.toLocaleString();

    return `${chartItems.length} segments • ${sessionsText} sessions • ${mauText} MAU`;
  };

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
        {renderSummaryLine()}
      </Text>

      {showFullSkeleton ? (
        <div className={classes.skeletonContainer}>
          <ChartSkeleton height={360} showLegend />
          <TableSkeleton columns={5} rows={5} />
        </div>
      ) : hasData ? (
        <>
          <div className={classes.chartWrapper}>
            {hasChartSeries ? (
              <BarChart height={360} option={barChartOption} />
            ) : isAnyLoading ? (
              <ChartSkeleton height={360} showLegend />
            ) : (
              <Text size="sm" c="dimmed" ta="center" py="xl">
                Chart unavailable
              </Text>
            )}
          </div>
          <div className={classes.tableWrapper}>
            <table className={classes.breakdownTable}>
              <thead>
                <tr>
                  <th>Segment</th>
                  <th>
                    <Group gap={4} wrap="nowrap" align="center">
                      <span>DAU</span>
                      <Tooltip
                        label="Daily Active Users — unique users active in the last 24 hours. Users are identified by installation ID, not login identity."
                        withArrow
                        multiline
                        w={220}
                      >
                        <IconInfoCircle
                          size={12}
                          style={{
                            opacity: 0.5,
                            cursor: "help",
                            flexShrink: 0,
                          }}
                        />
                      </Tooltip>
                    </Group>
                  </th>
                  <th>
                    <Group gap={4} wrap="nowrap" align="center">
                      <span>WAU</span>
                      <Tooltip
                        label="Weekly Active Users — unique users active in the last 7 days. Users are identified by installation ID, not login identity."
                        withArrow
                        multiline
                        w={220}
                      >
                        <IconInfoCircle
                          size={12}
                          style={{
                            opacity: 0.5,
                            cursor: "help",
                            flexShrink: 0,
                          }}
                        />
                      </Tooltip>
                    </Group>
                  </th>
                  <th>
                    <Group gap={4} wrap="nowrap" align="center">
                      <span>MAU</span>
                      <Tooltip
                        label="Monthly Active Users — unique users active in the last 30 days. Users are identified by installation ID, not login identity."
                        withArrow
                        multiline
                        w={220}
                      >
                        <IconInfoCircle
                          size={12}
                          style={{
                            opacity: 0.5,
                            cursor: "help",
                            flexShrink: 0,
                          }}
                        />
                      </Tooltip>
                    </Group>
                  </th>
                  <th>Sessions</th>
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
                    <td>
                      {renderMetricCell(
                        item.dau,
                        dauStatus.loading,
                        dauStatus.failed,
                      )}
                    </td>
                    <td>
                      {renderMetricCell(
                        item.wau,
                        wauStatus.loading,
                        wauStatus.failed,
                      )}
                    </td>
                    <td>
                      {renderMetricCell(
                        item.mau,
                        mauStatus.loading,
                        mauStatus.failed,
                      )}
                    </td>
                    <td>
                      {renderMetricCell(
                        item.sessions,
                        mauStatus.loading,
                        mauStatus.failed,
                      )}
                    </td>
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
