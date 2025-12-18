import { Box, Text, Group } from "@mantine/core";
import { useState, useMemo, useEffect } from "react";
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
  CRITICAL_INTERACTION_QUICK_TIME_FILTERS,
  CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS,
} from "../../constants";
import { useExceptionListData } from "./components/ExceptionTable/hooks";
import { useFilterStore } from "../../stores/useFilterStore";
import dayjs from "dayjs";
import { useAnalytics } from "../../hooks/useAnalytics";

export const AppVitals: React.FC = () => {
  const {
    startTime: storeStartTime,
    endTime: storeEndTime,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    handleTimeFilterChange: storeHandleTimeFilterChange,
    setQuickTimeRange,
  } = useFilterStore();
  const { trackTabSwitch, trackFilter } = useAnalytics("AppVitals");

  const [filters, setFilters] = useState<VitalsFiltersType>({
    issueType: ISSUE_TYPES.CRASHES,
    appVersion: "all",
    osVersion: "all",
    device: "all",
  });

  const getDefaultForamttedTimeRange = () => {
    return {
      startDate: dayjs.utc().subtract(7, "days").startOf("day").toISOString(),
      endDate: dayjs.utc().endOf("day").toISOString(),
    };
  };

  // Use store values if available, otherwise use defaults (reactive to store changes)
  const startTime = useMemo(() => {
    return storeStartTime || getDefaultForamttedTimeRange().startDate;
  }, [storeStartTime]);

  const endTime = useMemo(() => {
    return storeEndTime || getDefaultForamttedTimeRange().endDate;
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

  // Initialize filter store with LAST_7_DAYS on mount if not already set
  useEffect(() => {
    if (!storeStartTime || !storeEndTime) {
      const defaultRange = getDefaultForamttedTimeRange();
      setQuickTimeRange(CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_7_DAYS, 9);
      storeHandleTimeFilterChange({
        startDate: defaultRange.startDate,
        endDate: defaultRange.endDate,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
    // Update store with new time values
    // The store's handleTimeFilterChange only updates if filterValues exists,
    // so we also directly update startTime and endTime for AppVitals
    storeHandleTimeFilterChange(value);

    // Directly update startTime and endTime in store (AppVitals doesn't use filterValues)
    const store = useFilterStore.getState();
    // Get the quickTimeRangeString from the activeQuickTimeFilter index
    const activeIndex = store.activeQuickTimeFilter;
    const quickTimeString = activeIndex !== -1 && activeIndex < CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS.length
      ? CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[activeIndex].value
      : "";
    
    store.handleFilterChange(
      {} as any, // Empty filter values for AppVitals
      value.startDate || "",
      value.endDate || "",
      quickTimeString,
    );
    // Also update quickTimeRangeFilterIndex
    store.setQuickTimeRange(quickTimeString, activeIndex);
  };

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
                  : 9
              }
              defaultQuickTimeFilterString={
                quickTimeRangeString ||
                CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_7_DAYS
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
        />
        <ANRMetricsStats
          startTime={formattedStartTime}
          endTime={formattedEndTime}
          appVersion={filters.appVersion}
          osVersion={filters.osVersion}
          device={filters.device}
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
