import { Box, Text, Group } from "@mantine/core";
import { useState, useMemo, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import classes from "./AppVitals.module.css";
import { ISSUE_TYPES, GRAPH_CONFIGS, IssueType } from "./AppVitals.constants";
import type { VitalsFilters as VitalsFiltersType } from "./AppVitals.interface";
import {
  CrashTrendGraph,
  ANRTrendGraph,
  NonFatalTrendGraph,
  VitalsFilters,
  VitalsHeaderFilters,
  CrashList,
  ANRList,
  NonFatalList,
  CrashMetricsStats,
  ANRMetricsStats,
  AlertStatusStats,
} from "./components";
import DateTimeRangePicker from "../CriticalInteractionDetails/components/DateTimeRangePicker/DateTimeRangePicker";
import { StartEndDateTimeType } from "../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import { 
  DEFAULT_QUICK_TIME_FILTER,
  DEFAULT_QUICK_TIME_FILTER_INDEX,
} from "../../constants";
import { useExceptionListData } from "./components/ExceptionTable/hooks";
import { useFilterStore } from "../../stores/useFilterStore";
import dayjs from "dayjs";
import { useAnalytics, useGetAppStats } from "../../hooks";
import { getStartAndEndDateTimeString } from "../../utils/DateUtil";

export const AppVitals: React.FC = () => {
  const [searchParams] = useSearchParams();
  const {
    startTime: storeStartTime,
    endTime: storeEndTime,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    handleTimeFilterChange: storeHandleTimeFilterChange,
    initializeFromUrlParams,
  } = useFilterStore();
  const { trackTabSwitch, trackFilter } = useAnalytics("AppVitals");

  const [filters, setFilters] = useState<VitalsFiltersType>({
    issueType: ISSUE_TYPES.CRASHES,
    appVersion: "all",
    osVersion: "all",
    device: "all",
  });

  // Initialize default time range (Last 24 hours)
  const getDefaultTimeRange = () => {
    return getStartAndEndDateTimeString(DEFAULT_QUICK_TIME_FILTER, 2);
  };

  // Use store values if available, otherwise use defaults (reactive to store changes)
  const startTime = useMemo(() => {
    return storeStartTime || getDefaultTimeRange().startDate;
  }, [storeStartTime]);

  const endTime = useMemo(() => {
    return storeEndTime || getDefaultTimeRange().endDate;
  }, [storeEndTime]);

  // Format times to UTC ISO strings for API calls
  // Handles both "YYYY-MM-DD HH:mm:ss" and ISO format inputs
  // Always ensures UTC timezone
  const formattedStartTime = useMemo(() => {
    if (!startTime) return "";
    try {
      // Check if it's already in ISO format (contains 'T' or 'Z')
      if (startTime.includes("T") || startTime.includes("Z")) {
        // Already ISO format, parse and ensure UTC
        return dayjs.utc(startTime).toISOString();
      } else {
        // Format "YYYY-MM-DD HH:mm:ss" - parse as UTC and convert to ISO
        return dayjs.utc(startTime, "YYYY-MM-DD HH:mm:ss").toISOString();
      }
    } catch {
      return "";
    }
  }, [startTime]);

  const formattedEndTime = useMemo(() => {
    if (!endTime) return "";
    try {
      // Check if it's already in ISO format (contains 'T' or 'Z')
      if (endTime.includes("T") || endTime.includes("Z")) {
        // Already ISO format, parse and ensure UTC
        return dayjs.utc(endTime).toISOString();
      } else {
        // Format "YYYY-MM-DD HH:mm:ss" - parse as UTC and convert to ISO
        return dayjs.utc(endTime, "YYYY-MM-DD HH:mm:ss").toISOString();
      }
    } catch {
      return "";
    }
  }, [endTime]);

  // Initialize filter store from URL params
  useEffect(() => {
    initializeFromUrlParams(searchParams);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const handleIssueTypeChange = (value: string) => {
    trackTabSwitch(value);
    setFilters((prev) => ({ ...prev, issueType: value as IssueType }));
  };

  const handleAppVersionChange = (value: string | null) => {
    trackFilter("appVersion", value || "all");
    setFilters((prev) => ({ ...prev, appVersion: value || "all" }));
  };

  const handleOsVersionChange = (value: string | null) => {
    trackFilter("osVersion", value || "all");
    setFilters((prev) => ({ ...prev, osVersion: value || "all" }));
  };

  const handleDeviceChange = (value: string | null) => {
    trackFilter("device", value || "all");
    setFilters((prev) => ({ ...prev, device: value || "all" }));
  };

  const handleTimeFilterChange = (value: StartEndDateTimeType) => {
    storeHandleTimeFilterChange(value);
  };

  // Fetch total users and sessions from app_start spans (TRACES table)
  const { data: appStats } = useGetAppStats({
    startTime: formattedStartTime,
    endTime: formattedEndTime,
    appVersion: filters.appVersion,
    osVersion: filters.osVersion,
    device: filters.device,
  });

  // Fetch data from API for stats calculation
  const { exceptions: crashes } = useExceptionListData({
    startTime: formattedStartTime,
    endTime: formattedEndTime,
    appVersion: filters.appVersion,
    osVersion: filters.osVersion,
    device: filters.device,
    exceptionType: "crash",
  });

  const { exceptions: anrs } = useExceptionListData({
    startTime: formattedStartTime,
    endTime: formattedEndTime,
    appVersion: filters.appVersion,
    osVersion: filters.osVersion,
    device: filters.device,
    exceptionType: "anr",
  });

  const { exceptions: nonFatals } = useExceptionListData({
    startTime: formattedStartTime,
    endTime: formattedEndTime,
    appVersion: filters.appVersion,
    osVersion: filters.osVersion,
    device: filters.device,
    exceptionType: "nonfatal",
  });

  // Calculate stats based on API data (for filters display)
  const stats = useMemo(() => {
    return {
      crashes: crashes.length,
      anrs: anrs.length,
      nonFatals: nonFatals.length,
    };
  }, [crashes, anrs, nonFatals]);

  // Get graph config based on selected issue type
  const graphConfig = GRAPH_CONFIGS[filters.issueType];

  // Render appropriate list based on filter
  const renderIssueList = () => {
    switch (filters.issueType) {
      case ISSUE_TYPES.CRASHES:
        return (
          <CrashList
            startTime={formattedStartTime}
            endTime={formattedEndTime}
            appVersion={filters.appVersion}
            osVersion={filters.osVersion}
            device={filters.device}
          />
        );
      case ISSUE_TYPES.ANRS:
        return (
          <ANRList
            startTime={formattedStartTime}
            endTime={formattedEndTime}
            appVersion={
              filters.appVersion !== "all" ? filters.appVersion : undefined
            }
            osVersion={
              filters.osVersion !== "all" ? filters.osVersion : undefined
            }
            device={filters.device !== "all" ? filters.device : undefined}
          />
        );
      case ISSUE_TYPES.NON_FATALS:
        return (
          <NonFatalList
            startTime={formattedStartTime}
            endTime={formattedEndTime}
            appVersion={
              filters.appVersion !== "all" ? filters.appVersion : undefined
            }
            osVersion={
              filters.osVersion !== "all" ? filters.osVersion : undefined
            }
            device={filters.device !== "all" ? filters.device : undefined}
          />
        );
      default:
        return null;
    }
  };

  return (
    <Box className={classes.pageContainer}>
      {/* Header - Everything in one line */}
      <Box className={classes.pageHeader}>
        <Group justify="space-between" align="center" wrap="nowrap">
          {/* Left: Title */}
          <Box style={{ flexShrink: 0 }}>
            <div className={classes.titleSection}>
              <h1 className={classes.pageTitle}>App Vitals</h1>
            </div>
          </Box>

          {/* Right: Tabs, Filters, and Duration */}
          <Group gap="md" style={{ flexShrink: 0 }} wrap="nowrap">
            <VitalsFilters
              issueType={filters.issueType}
              onIssueTypeChange={handleIssueTypeChange}
              stats={stats}
            />
            <VitalsHeaderFilters
              appVersion={filters.appVersion}
              onAppVersionChange={handleAppVersionChange}
              osVersion={filters.osVersion}
              onOsVersionChange={handleOsVersionChange}
              device={filters.device}
              onDeviceChange={handleDeviceChange}
            />
            <DateTimeRangePicker
              handleTimefilterChange={handleTimeFilterChange}
              selectedQuickTimeFilterIndex={
                quickTimeRangeFilterIndex !== null
                  ? quickTimeRangeFilterIndex
                  : DEFAULT_QUICK_TIME_FILTER_INDEX
              }
              defaultQuickTimeFilterIndex={DEFAULT_QUICK_TIME_FILTER_INDEX}
              defaultQuickTimeFilterString={
                quickTimeRangeString || DEFAULT_QUICK_TIME_FILTER
              }
              defaultEndTime={endTime}
              defaultStartTime={startTime}
              showRefreshButton={true}
            />
          </Group>
        </Group>

        {/* Subtitle below */}
        <Text size="xs" c="dimmed" mt="xs" style={{ fontSize: "11px" }}>
          Monitor crashes, ANRs, and non-fatal issues in real-time
        </Text>
      </Box>

      {/* Stats Cards - 3 Sections */}
      <Box className={classes.statsContainer}>
        <CrashMetricsStats
          startTime={formattedStartTime}
          endTime={formattedEndTime}
          appVersion={filters.appVersion}
          osVersion={filters.osVersion}
          device={filters.device}
          externalTotalUsers={appStats?.totalUsers}
          externalTotalSessions={appStats?.totalSessions}
        />
        <ANRMetricsStats
          startTime={formattedStartTime}
          endTime={formattedEndTime}
          appVersion={filters.appVersion}
          osVersion={filters.osVersion}
          device={filters.device}
          externalTotalUsers={appStats?.totalUsers}
          externalTotalSessions={appStats?.totalSessions}
        />
        <AlertStatusStats
          startTime={formattedStartTime}
          endTime={formattedEndTime}
        />
      </Box>

      {/* Trend Graph */}
      {filters.issueType === ISSUE_TYPES.CRASHES && (
        <CrashTrendGraph
          startTime={formattedStartTime}
          endTime={formattedEndTime}
          appVersion={filters.appVersion}
          osVersion={filters.osVersion}
          device={filters.device}
          title={graphConfig.title}
          lineColor={graphConfig.color}
        />
      )}
      {filters.issueType === ISSUE_TYPES.ANRS && (
        <ANRTrendGraph
          startTime={formattedStartTime}
          endTime={formattedEndTime}
          appVersion={filters.appVersion}
          osVersion={filters.osVersion}
          device={filters.device}
          title={graphConfig.title}
          lineColor={graphConfig.color}
        />
      )}
      {filters.issueType === ISSUE_TYPES.NON_FATALS && (
        <NonFatalTrendGraph
          startTime={formattedStartTime}
          endTime={formattedEndTime}
          appVersion={filters.appVersion}
          osVersion={filters.osVersion}
          device={filters.device}
          title={graphConfig.title}
          lineColor={graphConfig.color}
        />
      )}

      {/* Issue List */}
      {renderIssueList()}
    </Box>
  );
};
