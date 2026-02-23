import { Box, TextInput, Group, ScrollArea, Select } from "@mantine/core";
import { useState, useMemo, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useDebouncedValue } from "@mantine/hooks";
import { NetworkListProps, NetworkApi } from "./NetworkList.interface";
import classes from "./NetworkList.module.css";
import { NetworkApiCard } from "../ScreenDetail/components/NetworkApiCard";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { CardSkeleton } from "../../components/Skeletons";
import {
  ROUTES,
  DEFAULT_QUICK_TIME_FILTER,
  DEFAULT_QUICK_TIME_FILTER_INDEX,
} from "../../constants";
import {
  DataQueryRequestBody,
  useGetDataQuery,
} from "../../hooks/useGetDataQuery";
import DateTimeRangePicker from "../CriticalInteractionDetails/components/DateTimeRangePicker/DateTimeRangePicker";
import { StartEndDateTimeType } from "../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import { getStartAndEndDateTimeString } from "../../utils/DateUtil";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { encodeNetworkId } from "./utils/networkIdUtils";
import { STATUS_CODE, PulseType } from "../../constants/PulseOtelSemcov";
import { useAnalytics } from "../../hooks/useAnalytics";
import { useFilterStore } from "../../stores/useFilterStore";

dayjs.extend(utc);

type SearchFilterType =
  | "method"
  | "url"
  | "endpoint"
  | "status_code"
  | "operation_name";

export function NetworkList({
  screenName,
  showHeader = true,
  showFilters = true,
  externalStartTime,
  externalEndTime,
  externalFilters = [],
}: NetworkListProps) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { trackClick, trackSearch, trackFilter } = useAnalytics("NetworkList");
  const [searchStr, setSearchStr] = useState<string>("");
  const [debouncedSearchStr] = useDebouncedValue(searchStr, 300);
  const [searchFilterType, setSearchFilterType] =
    useState<SearchFilterType>("url");

  // Use filter store for time range state (like AppVitals does)
  const {
    startTime: storeStartTime,
    endTime: storeEndTime,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    handleTimeFilterChange: storeHandleTimeFilterChange,
    initializeFromUrlParams,
    selectedTimeFilter,
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

  // Use external time if provided, otherwise use store values
  const startTime = externalStartTime || storeStartTime || getDefaultTimeRange().startDate;
  const endTime = externalEndTime || storeEndTime || getDefaultTimeRange().endDate;

  const handleTimeFilterChange = (value: StartEndDateTimeType) => {
    storeHandleTimeFilterChange(value);
  };

  // Query network APIs
  const requestBody = useMemo((): DataQueryRequestBody => {
    const filters: Array<{
      field: string;
      operator: "LIKE" | "EQ";
      value: string[];
    }> = [
      {
        field: "PulseType",
        operator: "LIKE" as const,
        value: ["%network%"],
      },
    ];

    // Add screen name filter only if provided
    if (screenName) {
      filters.push({
        field: `SpanAttributes['${PulseType.SCREEN_NAME}']`,
        operator: "EQ" as const,
        value: [screenName],
      });
    }

    // Add search filter based on selected filter type (using debounced value)
    if (showFilters && debouncedSearchStr.trim()) {
      const searchPattern = `%${debouncedSearchStr.trim()}%`;
      let filterField: string;

      switch (searchFilterType) {
        case "method":
          filterField = "SpanAttributes['http.method']";
          break;
        case "url":
        case "endpoint":
          filterField = "SpanAttributes['http.url']";
          break;
        case "status_code":
          filterField = "SpanAttributes['http.status_code']";
          break;
        case "operation_name":
          filterField = "SpanAttributes['graphql.operation.name']";
          break;
        default:
          filterField = "SpanAttributes['http.url']";
      }

      filters.push({
        field: filterField,
        operator: "LIKE" as const,
        value: [searchPattern],
      });
    }

    // Add external filters if provided
    if (externalFilters.length > 0) {
      filters.push(...externalFilters);
    }

    // Format times to UTC ISO format
    const formatToUTC = (time: string): string => {
      if (!time) return "";
      // If already in ISO format, parse and ensure valid
      if (time.includes("T") || time.includes("Z")) {
        return dayjs.utc(time).toISOString();
      }
      // Parse "YYYY-MM-DD HH:mm:ss" as UTC and convert to ISO format
      return dayjs.utc(time, "YYYY-MM-DD HH:mm:ss").toISOString();
    };


    // add screen name to select fields if provided

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
            expression: "SpanAttributes['http.url']",
          },
          alias: "url",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "SpanAttributes['graphql.operation.name']",
          },
          alias: "graphql_operation_name",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "SpanAttributes['graphql.operation.type']",
          },
          alias: "graphql_operation_type",
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
          function: "CUSTOM" as const,
          param: {
            expression: "uniqCombined(SessionId)",
          },
          alias: "all_sessions",
        },
        // {
        //   function: "CUSTOM" as const,
        //   param: {
        //     expression: "SpanAttributes['http.status_code']",
        //   },
        //   alias: "status_code",
        // },
        ...(screenName ? [{
          function: "COL" as const,
          param: { field: `SpanAttributes['${PulseType.SCREEN_NAME}']` },
          alias: "screen_name",
        }] : []),
      ],
      groupBy: [
        "url",
        "graphql_operation_name",
        "graphql_operation_type",
        ...(screenName ? ["screen_name"] : []),
      ],
      orderBy: [
        {
          field: "total_requests",
          direction: "DESC",
        },
      ],
      filters,
    };
  }, [
    startTime,
    endTime,
    screenName,
    debouncedSearchStr,
    searchFilterType,
    externalFilters,
    showFilters,
  ]);

  // Track search queries
  useEffect(() => {
    if (debouncedSearchStr.trim()) {
      trackSearch(debouncedSearchStr, undefined);
    }
  }, [debouncedSearchStr, trackSearch]);

  const { data, isLoading, isError } = useGetDataQuery({
    requestBody,
    enabled: !!startTime && !!endTime,
  });

  // Transform API response to NetworkApi format
  const networkApis = useMemo<NetworkApi[]>(() => {
    // Use real API data
    if (!data?.data?.rows || data.data.rows.length === 0) {
      return [];
    }

    const fields = data.data.fields;
    const urlIndex = fields.indexOf("url");
    const graphqlOperationIndex = fields.indexOf("graphql_operation_name");
    const graphqlOperationTypeIndex = fields.indexOf("graphql_operation_type");
    const methodIndex = fields.indexOf("method");
    const totalRequestsIndex = fields.indexOf("total_requests");
    const successRequestsIndex = fields.indexOf("success_requests");
    const responseTimeIndex = fields.indexOf("response_time");
    const allSessionsIndex = fields.indexOf("all_sessions");

    const aggregated = new Map<string, NetworkApi & { totalRequests: number; successRequests: number; responseTimeTotal: number }>();

    data.data.rows.forEach((row) => {
      const url = row[urlIndex] || "";
      const operationNameRaw = row[graphqlOperationIndex] || "";
      const operationTypeRaw = row[graphqlOperationTypeIndex] || "";
      const operationName = String(operationNameRaw).trim() || "";
      const operationType = String(operationTypeRaw).trim().toUpperCase() || "";
      const method =
        methodIndex >= 0 && row[methodIndex]
          ? String(row[methodIndex])
          : undefined;
      const totalRequests = parseFloat(row[totalRequestsIndex]) || 0;
      const successRequests = parseFloat(row[successRequestsIndex]) || 0;
      const responseTime = parseFloat(row[responseTimeIndex]) || 0;
      const responseTimeTotal = responseTime * totalRequests;

      const aggregationKey = `${url}||${operationName}||${operationType}`;
      const existing = aggregated.get(aggregationKey);
      if (existing) {
        existing.totalRequests += totalRequests;
        existing.successRequests += successRequests;
        existing.responseTimeTotal += responseTimeTotal;
        existing.requestCount = Math.round(existing.totalRequests);
        existing.successRate =
          existing.totalRequests > 0
            ? Math.round((existing.successRequests / existing.totalRequests) * 1000) / 10
            : 0;
        existing.errorRate =
          existing.totalRequests > 0
            ? Math.round(((existing.totalRequests - existing.successRequests) / existing.totalRequests) * 1000) / 10
            : 0;
        existing.avgResponseTime =
          existing.totalRequests > 0
            ? Math.round(existing.responseTimeTotal / existing.totalRequests)
            : 0;
        existing.allSessions = Math.max(existing.allSessions || 0, Math.round(parseFloat(row[allSessionsIndex]) || 0));
        return;
      }

      // Generate unique ID from endpoint URL (base64 encoded for URL safety)
      const id = encodeNetworkId(
        url,
        operationName ? String(operationName) : undefined,
        operationType ? String(operationType) : undefined
      );

      aggregated.set(aggregationKey, {
        id,
        endpoint: url,
        operationName: operationName ? String(operationName) : undefined,
        operationType: operationType ? String(operationType) : undefined,
        method,
        avgResponseTime: Math.round(responseTime || 0),
        requestCount: Math.round(totalRequests),
        successRate:
          totalRequests > 0 ? Math.round((successRequests / totalRequests) * 1000) / 10 : 0,
        errorRate:
          totalRequests > 0 ? Math.round(((totalRequests - successRequests) / totalRequests) * 1000) / 10 : 0,
        p50: 0, // Not available in new contract
        p95: 0, // Not available in new contract
        p99: 0, // Not available in new contract
        lastCalled: new Date().toISOString(), // Not available in new contract
        screenName: screenName || undefined,
        allSessions: Math.round(parseFloat(row[allSessionsIndex]) || 0),
        totalRequests,
        successRequests,
        responseTimeTotal,
      });
    });
    return Array.from(aggregated.values());
  }, [data, screenName]);

  // APIs are already filtered by the API query based on searchFilterType
  const filteredAndSortedApis = useMemo(() => {
    return networkApis;
  }, [networkApis]);

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSearchStr(event.target.value);
  };

  const onApiClick = (apiId: string) => {
    trackClick(`NetworkAPI: ${apiId}`);
    const url = `${ROUTES.NETWORK_DETAIL.basePath}/${apiId}`;
    if (screenName) {
      navigate(`${url}?screenName=${encodeURIComponent(screenName)}`);
    } else {
      navigate(url);
    }
  };

  const renderContent = () => {
    // Show loading state with skeleton cards
    if (isLoading) {
      return (
        <ScrollArea className={classes.scrollArea}>
          <Box className={classes.apiListContainer}>
            {Array.from({ length: 6 }).map((_, index) => (
              <CardSkeleton
                key={index}
                height={100}
                showHeader
                contentRows={1}
              />
            ))}
          </Box>
        </ScrollArea>
      );
    }

    // Show error state
    if (isError && networkApis.length === 0) {
      return (
        <ErrorAndEmptyState
          classes={[classes.error]}
          message="Failed to load network APIs. Please try again."
        />
      );
    }

    // Show empty state when no APIs match filters or no data available
    if (filteredAndSortedApis.length === 0) {
      const emptyMessage = searchStr
        ? "No network APIs match your search"
        : networkApis.length === 0
          ? "No network APIs found"
          : "No network APIs match your filters";

      return (
        <ErrorAndEmptyState classes={[classes.error]} message={emptyMessage} />
      );
    }

    return (
      <ScrollArea className={classes.scrollArea}>
        <Box className={classes.apiListContainer}>
          {filteredAndSortedApis.map((api) => (
            <NetworkApiCard
              key={api.id}
              apiData={api}
              onClick={() => onApiClick(api.id)}
            />
          ))}
        </Box>
      </ScrollArea>
    );
  };

  return (
    <Box className={classes.pageContainer}>
      {/* Header */}
      {showHeader && (
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <h1 className={classes.pageTitle}>Network APIs</h1>
            <span className={classes.apiCount}>
              {filteredAndSortedApis.length}{" "}
              {filteredAndSortedApis.length === 1 ? "API" : "APIs"}
            </span>
          </Box>
        </Box>
      )}

      {/* Search and Filter Section */}
      {showFilters && (
        <Box className={classes.controlsSection}>
          <Group className={classes.searchBarContainer}>
            {/* Combined Search Filter - Select + TextInput */}
            <Group gap={0} style={{ flex: 1 }}>
              <Select
                value={searchFilterType}
                onChange={(value) => {
                  const newType = (value as SearchFilterType) || "url";
                  trackFilter("searchFilterType", newType);
                  setSearchFilterType(newType);
                  setSearchStr(""); // Clear search value when filter type changes
                }}
                data={[
                  { value: "method", label: "Method" },
                  { value: "url", label: "URL" },
                  { value: "status_code", label: "Status Code" },
                  { value: "operation_name", label: "Operation Name" },
                ]}
                size="sm"
                style={{
                  width: "140px",
                  flexShrink: 0,
                }}
                styles={{
                  input: {
                    borderTopRightRadius: 0,
                    borderBottomRightRadius: 0,
                    borderRight: "none",
                  },
                }}
              />
              <TextInput
                placeholder={
                  searchFilterType === "method"
                    ? "Search by method (e.g., GET, POST)..."
                    : searchFilterType === "url" ||
                        searchFilterType === "endpoint"
                      ? "Search by URL/endpoint..."
                      : searchFilterType === "operation_name"
                        ? "Search by GraphQL operation name..."
                      : "Search by status code (e.g., 200, 404)..."
                }
                onChange={handleSearchChange}
                size="sm"
                value={searchStr}
                style={{ flex: 1 }}
                styles={{
                  input: {
                    borderTopLeftRadius: 0,
                    borderBottomLeftRadius: 0,
                  },
                }}
              />
            </Group>
            <DateTimeRangePicker
              handleTimefilterChange={handleTimeFilterChange}
              selectedQuickTimeFilterIndex={quickTimeRangeFilterIndex !== null ? quickTimeRangeFilterIndex : DEFAULT_QUICK_TIME_FILTER_INDEX}
              defaultQuickTimeFilterIndex={DEFAULT_QUICK_TIME_FILTER_INDEX}
              defaultQuickTimeFilterString={quickTimeRangeString || DEFAULT_QUICK_TIME_FILTER}
              defaultEndTime={selectedTimeFilter?.endDate || endTime}
              defaultStartTime={selectedTimeFilter?.startDate || startTime}
            />
          </Group>
        </Box>
      )}

      {/* Content */}
      {renderContent()}
    </Box>
  );
}
