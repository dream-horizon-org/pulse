import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Button, Box, Text, SimpleGrid, Group, Badge } from "@mantine/core";
import { IconX } from "@tabler/icons-react";
import { IconArrowLeft } from "@tabler/icons-react";
import { NetworkDetailProps, ApiCallMetrics } from "./NetworkDetail.interface";
import classes from "./NetworkDetail.module.css";
import vitalsClasses from "../AppVitals/AppVitals.module.css";
import { useMemo, useState, useEffect } from "react";
import { ErrorBreakdown } from "./components/ErrorBreakdown";
import { NetworkIssuesByProvider } from "./components/NetworkIssuesByProvider/NetworkIssuesByProvider";
import { decodeNetworkId } from "../NetworkList/utils/networkIdUtils";
import {
  useGetDataQuery,
  DataQueryRequestBody,
} from "../../hooks/useGetDataQuery";
import { 
  DEFAULT_QUICK_TIME_FILTER,
  DEFAULT_QUICK_TIME_FILTER_INDEX,
} from "../../constants";
import { getStartAndEndDateTimeString } from "../../utils/DateUtil";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { SkeletonLoader, MetricsGridSkeleton, ChartSkeleton } from "../../components/Skeletons";
import DateTimeRangePicker from "../CriticalInteractionDetails/components/DateTimeRangePicker/DateTimeRangePicker";
import { StartEndDateTimeType } from "../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import {
  NetworkFilters,
  AppliedFilter,
  FilterType,
  FILTER_OPTIONS,
} from "./components/NetworkFilters";
import { STATUS_CODE, SpanType } from "../../constants/PulseOtelSemcov";
import { useFilterStore } from "../../stores/useFilterStore";

dayjs.extend(utc);

export function NetworkDetail(_props: NetworkDetailProps) {
  const navigate = useNavigate();
  const { apiId } = useParams<{
    apiId: string;
  }>();
  const [searchParams] = useSearchParams();

  // Get screen name from URL query params (if navigating from ScreenDetail)
  const screenNameFromUrl = searchParams.get("screenName");

  // Decode the API ID to get method and url
  const decodedApiData = useMemo(() => {
    if (!apiId) return null;
    return decodeNetworkId(apiId);
  }, [apiId]);

  // Applied filters state - initialize with screen name if present in URL
  const [appliedFilters, setAppliedFilters] = useState<AppliedFilter[]>(() => {
    if (screenNameFromUrl) {
      return [
        {
          type: "ScreenName" as FilterType,
          value: screenNameFromUrl,
          id: `ScreenName-${screenNameFromUrl}-${Date.now()}`,
        },
      ];
    }
    return [];
  });

  // Use filter store for time range state
  const {
    startTime: storeStartTime,
    endTime: storeEndTime,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    handleTimeFilterChange: storeHandleTimeFilterChange,
    initializeFromUrlParams,
  } = useFilterStore();

  // Initialize default time range (Last 24 hours)
  const getDefaultTimeRange = () => {
    return getStartAndEndDateTimeString(DEFAULT_QUICK_TIME_FILTER, 2);
  };

  // Initialize filter store from URL params
  useEffect(() => {
    initializeFromUrlParams(searchParams);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  // Use store values for time range
  const startTime = storeStartTime || getDefaultTimeRange().startDate;
  const endTime = storeEndTime || getDefaultTimeRange().endDate;

  const handleTimeFilterChange = (value: StartEndDateTimeType) => {
    storeHandleTimeFilterChange(value);
  };

  const handleAddFilter = (type: FilterType, value: string) => {
    const newFilter: AppliedFilter = {
      type,
      value,
      id: `${type}-${value}-${Date.now()}`,
    };
    setAppliedFilters((prev) => [...prev, newFilter]);
  };

  const handleRemoveFilter = (id: string) => {
    setAppliedFilters((prev) => prev.filter((f) => f.id !== id));
  };

  // Format times to UTC ISO format
  const formatToUTC = (time: string): string => {
    if (!time) return "";
    if (time.includes("T") || time.includes("Z")) {
      return time;
    }
    return dayjs.utc(time).toISOString();
  };

  // Build common filters from applied filters
  const buildCommonFilters = useMemo(() => {
    const filterArray: Array<{
      field: string;
      operator: "LIKE" | "EQ";
      value: string[];
    }> = [];

    appliedFilters.forEach((filter) => {
      let field: string;
      let operator: "LIKE" | "EQ" = "EQ";

      switch (filter.type) {
        case "AppVersionCode":
          field = "AppVersionCode";
          break;
        case "DeviceModel":
          field = "DeviceModel";
          break;
        case "Platform":
          field = "Platform";
          break;
        case "GeoState":
          field = "GeoState";
          break;
        case "OsVersion":
          field = "OsVersion";
          break;
        case "ScreenName":
          field = `SpanAttributes['${SpanType.SCREEN_NAME}']`;
          break;
        case "InteractionName":
          // Special case: use custom expression with LIKE
          field = "SpanAttributes['pulse.interaction.active.names']";
          operator = "LIKE";
          filterArray.push({
            field,
            operator,
            value: [`%${filter.value}%`],
          });
          return; // Skip the default push below
        default:
          return; // Skip unknown filter types
      }

      filterArray.push({
        field,
        operator,
        value: [filter.value],
      });
    });

    return filterArray;
  }, [appliedFilters]);

  // Query network API details
  const requestBody = useMemo((): DataQueryRequestBody => {
    // Provide a default requestBody to prevent null access errors
    // The query will be disabled via enabled flag if decodedApiData is null
    if (!decodedApiData || !startTime || !endTime) {
      const defaultTimeRange = getDefaultTimeRange();
      return {
        dataType: "TRACES" as const,
        timeRange: {
          start: formatToUTC(startTime || defaultTimeRange.startDate),
          end: formatToUTC(endTime || defaultTimeRange.endDate),
        },
        select: [],
        filters: [],
      };
    }

    const filters: Array<{
      field: string;
      operator: "LIKE" | "EQ";
      value: string[];
    }> = [
      {
        field: "SpanType",
        operator: "LIKE" as const,
        value: ["%network%"],
      },
      {
        field: "SpanAttributes['http.method']",
        operator: "EQ" as const,
        value: [decodedApiData.method],
      },
      {
        field: "SpanAttributes['http.url']",
        operator: "EQ" as const,
        value: [decodedApiData.url],
      },
    ];

    // Add applied filters
    filters.push(...buildCommonFilters);

    return {
      dataType: "TRACES" as const,
      timeRange: {
        start: formatToUTC(startTime),
        end: formatToUTC(endTime),
      },
      select: [
        {
          function: "CUSTOM" as const,
          param: {
            expression: "SpanAttributes['http.method']",
          },
          alias: "method",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "SpanAttributes['http.url']",
          },
          alias: "url",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "count()",
          },
          alias: "total_requests",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `countIf(StatusCode != '${STATUS_CODE.ERROR}')`,
          },
          alias: "success_requests",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "avg(Duration)",
          },
          alias: "response_time",
        },
        {
          function: "DURATION_P50" as const,
          alias: "p50",
        },
        {
          function: "DURATION_P95" as const,
          alias: "p95",
        },
        {
          function: "DURATION_P99" as const,
          alias: "p99",
        },
      ],
      filters,
      groupBy: ["method", "url"],
    };
  }, [decodedApiData, startTime, endTime, buildCommonFilters]);

  const { data, isLoading, isError } = useGetDataQuery({
    requestBody,
    enabled: !!decodedApiData && !!startTime && !!endTime,
  });

  // Extract API data and metrics from response
  const apiData = useMemo(() => {
    if (!decodedApiData) {
      return {
        endpoint: "Unknown API",
        method: "UNKNOWN",
        screenName: undefined,
      };
    }
    return {
      endpoint: decodedApiData.url,
      method: decodedApiData.method,
      screenName: undefined, // Can be extracted from data if needed
    };
  }, [decodedApiData]);

  // Calculate metrics from API response
  const apiMetrics = useMemo<ApiCallMetrics>(() => {
    if (!data?.data?.rows || data.data.rows.length === 0) {
      return {
        avgRequestTime: 0,
        totalRequests: 0,
        successRate: 0,
        failureRate: 0,
        p50: 0,
        p95: 0,
        p99: 0,
      };
    }

    const fields = data.data.fields;
    const totalRequestsIndex = fields.indexOf("total_requests");
    const successRequestsIndex = fields.indexOf("success_requests");
    const responseTimeIndex = fields.indexOf("response_time");
    const p50Index = fields.indexOf("p50");
    const p95Index = fields.indexOf("p95");
    const p99Index = fields.indexOf("p99");

    const row = data.data.rows[0];
    const totalRequests = parseFloat(row[totalRequestsIndex]) || 0;
    const successRequests = parseFloat(row[successRequestsIndex]) || 0;
    const successRate =
      totalRequests > 0 ? (successRequests / totalRequests) * 100 : 0;
    const failureRate =
      totalRequests > 0
        ? ((totalRequests - successRequests) / totalRequests) * 100
        : 0;
    return {
      avgRequestTime: Math.round(parseFloat(row[responseTimeIndex]) / 1_000_000 || 0),
      totalRequests: Math.round(totalRequests),
      successRate: Math.round(successRate * 10) / 10,
      failureRate: Math.round(failureRate * 10) / 10,

      // p50, p95, p99 are already in milliseconds from backend
      // (quantileTDigestIf uses Duration / 1e6)
      p50: Math.round(parseFloat(row[p50Index]) || 0),
      p95: Math.round(parseFloat(row[p95Index]) || 0),
      p99: Math.round(parseFloat(row[p99Index]) || 0),
    };
  }, [data]);

  const handleBack = () => {
    navigate(-1);
  };

  // Show loading state with skeleton layout
  if (isLoading) {
    return (
      <div className={classes.pageContainer}>
        {/* Header skeleton */}
        <div className={classes.headerContainer}>
          <SkeletonLoader height={32} width={80} radius="md" />
          <div className={classes.titleSection}>
            <SkeletonLoader height={24} width={200} radius="sm" />
            <SkeletonLoader height={16} width={400} radius="sm" />
          </div>
        </div>

        {/* Filters skeleton */}
        <Box mb="xl" mt="md">
          <Group gap="md">
            <SkeletonLoader height={36} width={200} radius="md" />
            <SkeletonLoader height={36} width={250} radius="md" />
          </Group>
        </Box>

        {/* Stats skeleton */}
        <Box className={vitalsClasses.statsContainer}>
          <Box className={vitalsClasses.statSection}>
            <SkeletonLoader height={16} width={150} radius="sm" />
            <MetricsGridSkeleton count={2} />
          </Box>
          <Box className={vitalsClasses.statSection}>
            <SkeletonLoader height={16} width={120} radius="sm" />
            <MetricsGridSkeleton count={2} />
          </Box>
          <Box className={vitalsClasses.statSection}>
            <SkeletonLoader height={16} width={130} radius="sm" />
            <MetricsGridSkeleton count={3} />
          </Box>
        </Box>

        {/* Error breakdown skeleton */}
        <Box mt="xl">
          <SkeletonLoader height={18} width={180} radius="sm" />
          <SkeletonLoader height={14} width={350} radius="xs" />
          <SimpleGrid className={classes.errorBreakdownGrid} cols={{ base: 1, lg: 2 }} spacing="lg" mt="md">
            <ChartSkeleton height={200} />
            <ChartSkeleton height={200} />
          </SimpleGrid>
        </Box>
      </div>
    );
  }

  // Show error state
  if (isError || !decodedApiData) {
    return (
      <div className={classes.pageContainer}>
        <ErrorAndEmptyState
          message={
            !decodedApiData
              ? "Invalid API ID"
              : "Failed to load network details. Please try again."
          }
        />
      </div>
    );
  }

  const responseTimeFormatter = (responseTimeMs: number) => {
    // responseTimeMs is already in milliseconds (converted from nanoseconds earlier)
    // if the response time is greater than 1000ms, format as seconds
    if (responseTimeMs > 1000) {
      return `${(responseTimeMs / 1000).toFixed(1)}s`;
    }
    return `${responseTimeMs.toFixed(0)}ms`;
  };

  return (
    <div className={classes.pageContainer}>
      {/* Header */}
      <div className={classes.headerContainer}>
        <Button
          variant="subtle"
          leftSection={<IconArrowLeft size={16} />}
          onClick={handleBack}
          className={classes.backButton}
        >
          Back
        </Button>
        <div className={classes.titleSection}>
          <h1 className={classes.pageTitle}>Network Details</h1>
          <Text size="sm" c="dimmed" className={classes.subtitle}>
            {apiData.method} {apiData.endpoint}
          </Text>
        </div>
      </div>

      {/* Filters */}
      <Box mb="xl" mt="md">
        <Group gap="md" align="center" wrap="nowrap">
          {/* Network Filters */}
          <NetworkFilters
            appliedFilters={appliedFilters}
            onAddFilter={handleAddFilter}
            onRemoveFilter={handleRemoveFilter}
          />

          {/* DateTime Filter */}
          <DateTimeRangePicker
            handleTimefilterChange={handleTimeFilterChange}
            selectedQuickTimeFilterIndex={quickTimeRangeFilterIndex !== null ? quickTimeRangeFilterIndex : DEFAULT_QUICK_TIME_FILTER_INDEX}
            defaultQuickTimeFilterIndex={DEFAULT_QUICK_TIME_FILTER_INDEX}
            defaultQuickTimeFilterString={quickTimeRangeString || DEFAULT_QUICK_TIME_FILTER}
            defaultEndTime={endTime}
            defaultStartTime={startTime}
          />
        </Group>

        {/* Applied Filters Chips - Displayed in next row */}
        {appliedFilters.length > 0 && (
          <Group gap="xs" mt="md" wrap="wrap">
            {appliedFilters.map((filter) => {
              const filterLabel =
                FILTER_OPTIONS.find(
                  (opt: { value: string; label: string }) =>
                    opt.value === filter.type,
                )?.label || filter.type;
              return (
                <Badge
                  key={filter.id}
                  variant="light"
                  rightSection={
                    <IconX
                      size={12}
                      style={{ cursor: "pointer" }}
                      onClick={() => handleRemoveFilter(filter.id)}
                    />
                  }
                  size="lg"
                >
                  {filterLabel}: {filter.value}
                </Badge>
              );
            })}
          </Group>
        )}
      </Box>

      {/* Network Metrics */}
      <Box className={vitalsClasses.statsContainer}>
        {/* Network Metrics Cards */}
        <Box className={vitalsClasses.statSection}>
          <Text className={vitalsClasses.sectionTitle}>
            Network Performance
          </Text>
          <Box className={vitalsClasses.metricsGrid}>
            <Box className={vitalsClasses.statItem}>
              <Text className={vitalsClasses.statLabel}>Avg Request Time</Text>
              <Text className={vitalsClasses.statValue} c="blue">
                {responseTimeFormatter(apiMetrics.avgRequestTime)}
              </Text>
            </Box>
            <Box className={vitalsClasses.statItem}>
              <Text className={vitalsClasses.statLabel}>Total Requests</Text>
              <Text className={vitalsClasses.statValue} c="blue">
                {apiMetrics.totalRequests.toLocaleString()}
              </Text>
            </Box>
          </Box>
        </Box>

        {/* Request Success Rate */}
        <Box className={vitalsClasses.statSection}>
          <Text className={vitalsClasses.sectionTitle}>Success Rate</Text>
          <Box className={vitalsClasses.metricsGrid}>
            <Box className={vitalsClasses.statItem}>
              <Text className={vitalsClasses.statLabel}>
                Successful Requests
              </Text>
              <Text className={vitalsClasses.statValue} c="green">
                {apiMetrics.successRate}%
              </Text>
            </Box>
            <Box className={vitalsClasses.statItem}>
              <Text className={vitalsClasses.statLabel}>Failed Requests</Text>
              <Text className={vitalsClasses.statValue} c="red">
                {apiMetrics.failureRate}%
              </Text>
            </Box>
          </Box>
        </Box>

        {/* Response Time */}
        <Box className={vitalsClasses.statSection}>
          <Text className={vitalsClasses.sectionTitle}>Response Time</Text>
          <Box className={vitalsClasses.metricsGrid}>
            <Box className={vitalsClasses.statItem}>
              <Text className={vitalsClasses.statLabel}>P50</Text>
              <Text className={vitalsClasses.statValue} c="teal">
                {apiMetrics.p50}ms
              </Text>
            </Box>
            <Box className={vitalsClasses.statItem}>
              <Text className={vitalsClasses.statLabel}>P95</Text>
              <Text className={vitalsClasses.statValue} c="teal">
                {apiMetrics.p95}ms
              </Text>
            </Box>
            <Box className={vitalsClasses.statItem}>
              <Text className={vitalsClasses.statLabel}>P99</Text>
              <Text className={vitalsClasses.statValue} c="orange">
                {apiMetrics.p99}ms
              </Text>
            </Box>
          </Box>
        </Box>
      </Box>

      {/* Error Breakdown Section */}
      <Box mt="xl">
        <Box mb="md">
          <Text
            size="sm"
            fw={600}
            c="#0ba09a"
            mb={4}
            style={{ fontSize: "16px", letterSpacing: "-0.3px" }}
          >
            HTTP Error Analysis
          </Text>
          <Text size="xs" c="dimmed" style={{ fontSize: "12px" }}>
            Detailed breakdown of client and server errors by status code
          </Text>
        </Box>

        <SimpleGrid className={classes.errorBreakdownGrid} mih={200} cols={{ base: 1, lg: 2 }} spacing="lg">
          <ErrorBreakdown
            type="4xx"
            method={apiData.method}
            url={apiData.endpoint}
            startTime={formatToUTC(startTime)}
            endTime={formatToUTC(endTime)}
            additionalFilters={buildCommonFilters}
          />
          <ErrorBreakdown
            type="5xx"
            method={apiData.method}
            url={apiData.endpoint}
            startTime={formatToUTC(startTime)}
            endTime={formatToUTC(endTime)}
            additionalFilters={buildCommonFilters}
          />
        </SimpleGrid>
      </Box>

      {/* Network Issues Section */}
      <Box mt="xl">
        <NetworkIssuesByProvider
          method={apiData.method}
          url={apiData.endpoint}
          startTime={formatToUTC(startTime)}
          endTime={formatToUTC(endTime)}
          shouldFetch={true}
          additionalFilters={buildCommonFilters}
          showHeader={false}
        />
      </Box>
    </div>
  );
}
