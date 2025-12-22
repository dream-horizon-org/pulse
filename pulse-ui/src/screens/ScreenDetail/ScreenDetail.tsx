import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { SimpleGrid, Box, Text, Tabs, Title, Tooltip } from "@mantine/core";
import { IconArrowNarrowLeft } from "@tabler/icons-react";
import { ScreenDetailProps } from "./ScreenDetail.interface";
import classes from "./ScreenDetail.module.css";
import vitalsClasses from "../AppVitals/AppVitals.module.css";
import { useMemo, useState, useEffect } from "react";
import { TimeSpentGraph } from "./components/TimeSpentGraph";
import { NetworkList } from "../NetworkList/NetworkList";
import { useGetScreenEngagementData } from "./hooks/useGetScreenEngagementData";
import { UserEngagementGraph } from "../Home/components/UserEngagementGraph";
import { ActiveSessionsGraph } from "../Home/components/ActiveSessionsGraph";
import {
  CrashList,
  ANRList,
  NonFatalList,
  VitalsFilters,
  CrashTrendGraph,
  ANRTrendGraph,
  NonFatalTrendGraph,
  CrashMetricsStats,
  ANRMetricsStats,
} from "../AppVitals/components";
import {
  ISSUE_TYPES,
  IssueType,
  GRAPH_CONFIGS,
} from "../AppVitals/AppVitals.constants";
import DateTimeRangePicker from "../CriticalInteractionDetails/components/DateTimeRangePicker/DateTimeRangePicker";
import { StartEndDateTimeType } from "../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import {
  CRITICAL_INTERACTION_QUICK_TIME_FILTERS,
  CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS,
  ROUTES,
} from "../../constants";
import { useFilterStore } from "../../stores/useFilterStore";
import { getStartAndEndDateTimeString } from "../../utils/DateUtil";
import dayjs from "dayjs";
import { useExceptionListData } from "../AppVitals/components/ExceptionTable/hooks";
import { InteractionDetailsFilters } from "../CriticalInteractionDetails/components/InteractionDetailsFilters";

export function ScreenDetail(_props: ScreenDetailProps) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { screenName } = useParams<{ screenName: string }>();
  const decodedScreenName = screenName ? decodeURIComponent(screenName) : "";

  // Global filter store
  const {
    startTime: storeStartTime,
    endTime: storeEndTime,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    handleTimeFilterChange: storeHandleTimeFilterChange,
    setQuickTimeRange,
    initializeFromUrlParams
  } = useFilterStore();

  // Tab state
  const [activeTab, setActiveTab] = useState<string | null>("engagement");

  // Local filter state (app version, OS version, device)
  const [appVersion] = useState("all");
  const [osVersion] = useState("all");
  const [device] = useState("all");

  // Performance & Stability filters (separate state for issue type)
  const [issueType, setIssueType] = useState<IssueType>(ISSUE_TYPES.CRASHES);

  // Initialize default time values (LAST_1_HOUR) if not set in store
  const getDefaultTimeRange = () => {
    return getStartAndEndDateTimeString(
      CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_1_HOUR,
      2,
    );
  };

  // Use store values if available, otherwise use defaults
  const startTime = useMemo(() => {
    return storeStartTime || getDefaultTimeRange().startDate;
  }, [storeStartTime]);

  const endTime = useMemo(() => {
    return storeEndTime || getDefaultTimeRange().endDate;
  }, [storeEndTime]);

  // Initialize filter store with LAST_1_HOUR on mount if not already set
  useEffect(() => {
    if (!storeStartTime || !storeEndTime) {
      const defaultRange = getDefaultTimeRange();
      // Find the index for LAST_1_HOUR in the time filter options
      const last1HourIndex =
        CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS.findIndex(
          (option) =>
            option.value ===
            CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_1_HOUR,
        );
      setQuickTimeRange(
        CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_1_HOUR,
        last1HourIndex >= 0 ? last1HourIndex : 3,
      );
      storeHandleTimeFilterChange({
        startDate: defaultRange.startDate,
        endDate: defaultRange.endDate,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Filter handlers
  const handleTimeFilterChange = (value: StartEndDateTimeType) => {
    storeHandleTimeFilterChange(value);
    const store = useFilterStore.getState();
    store.handleFilterChange(
      {} as any,
      value.startDate || "",
      value.endDate || "",
    );
  };

  const handleIssueTypeChange = (value: string) => {
    setIssueType(value as IssueType);
  };

  const handleBack = () => {
    navigate(ROUTES.SCREENS.basePath);
  };

  // Format time for API calls (convert to ISO string)
  const formattedStartTime = useMemo(() => {
    if (!startTime) return "";
    try {
      return dayjs.utc(startTime).toISOString();
    } catch {
      return "";
    }
  }, [startTime]);

  const formattedEndTime = useMemo(() => {
    if (!endTime) return "";
    try {
      return dayjs.utc(endTime).toISOString();
    } catch {
      return "";
    }
  }, [endTime]);

  // Fetch data from API for stats calculation
  const { exceptions: crashes } = useExceptionListData({
    startTime: formattedStartTime,
    endTime: formattedEndTime,
    appVersion: appVersion !== "all" ? appVersion : undefined,
    osVersion: osVersion !== "all" ? osVersion : undefined,
    device: device !== "all" ? device : undefined,
    screenName: decodedScreenName,
    exceptionType: "crash",
  });

  const { exceptions: anrs } = useExceptionListData({
    startTime: formattedStartTime,
    endTime: formattedEndTime,
    appVersion: appVersion !== "all" ? appVersion : undefined,
    osVersion: osVersion !== "all" ? osVersion : undefined,
    device: device !== "all" ? device : undefined,
    screenName: decodedScreenName,
    exceptionType: "anr",
  });

  const { exceptions: nonFatals } = useExceptionListData({
    startTime: formattedStartTime,
    endTime: formattedEndTime,
    appVersion: appVersion !== "all" ? appVersion : undefined,
    osVersion: osVersion !== "all" ? osVersion : undefined,
    device: device !== "all" ? device : undefined,
    screenName: decodedScreenName,
    exceptionType: "nonfatal",
  });

  // Calculate stats for VitalsFilters (using API data)
  const vitalsStats = useMemo(() => {
    return {
      crashes: crashes.length,
      anrs: anrs.length,
      nonFatals: nonFatals.length,
      crashFreeUsers: 0,
      crashFreeSessions: 0,
      anrFreeUsers: 0,
      anrFreeSessions: 0,
      firingAlerts: 0,
      activeAlerts: 0,
    };
  }, [crashes, anrs, nonFatals]);

  // Filtered screen metrics - simulates API filtering (uses filters)
  // Fetch screen engagement data (time spent, sessions, load time)
  const {
    data: engagementData,
    isLoading: isLoadingEngagement,
    error: engagementError,
  } = useGetScreenEngagementData({
    screenName: decodedScreenName,
    startTime: startTime || "",
    endTime: endTime || "",
    appVersion: appVersion !== "all" ? appVersion : undefined,
    osVersion: osVersion !== "all" ? osVersion : undefined,
    device: device !== "all" ? device : undefined,
  });

  // Get graph config based on selected issue type
  const graphConfig = GRAPH_CONFIGS[issueType];

  useEffect(() => {
    initializeFromUrlParams(searchParams);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  return (
    <Tabs
      value={activeTab}
      onChange={setActiveTab}
      variant="unstyled"
      classNames={classes}
      className={classes.tabs}
    >
      <div className={classes.screenDetailContainer}>
        {/* Header - Consistent with CriticalInteractionDetails */}
        <div className={classes.screenDetailHeader}>
          {/* Left Section - Back Button, Title */}
          <div className={classes.screenDetailHeaderContent}>
            <Tooltip label="Back to screens">
              <span
                onClick={handleBack}
                className={classes.backButtonContainer}
              >
                <IconArrowNarrowLeft className={classes.backButton} size={18} />
              </span>
            </Tooltip>
            <div className={classes.titleSection}>
              <Title order={5} className={classes.pageTitle}>
                {decodedScreenName || "Screen Details"}
              </Title>
            </div>
          </div>

          {/* Right Section - Filters, Time Picker */}
          <div className={classes.headerRightSection}>
            <InteractionDetailsFilters />
            <div className={classes.verticalDivider} />
            <DateTimeRangePicker
              handleTimefilterChange={handleTimeFilterChange}
              selectedQuickTimeFilterIndex={quickTimeRangeFilterIndex || 0}
              defaultQuickTimeFilterString={quickTimeRangeString || ""}
              defaultEndTime={endTime}
              defaultStartTime={startTime}
            />
          </div>
        </div> 

        <Tabs.List>
          <Tabs.Tab value="engagement">User Engagement</Tabs.Tab>
          <Tabs.Tab value="performance">Performance & Stability</Tabs.Tab>
          <Tabs.Tab value="network">Network</Tabs.Tab>
        </Tabs.List>

        {/* User Engagement Tab */}
        <Tabs.Panel value="engagement">
          {/* Detailed Graphs */}
          <SimpleGrid cols={{ base: 1, lg: 3 }} spacing="md">
            <TimeSpentGraph
              avgTimeSpent={engagementData?.avgTimeSpent || 0}
              avgLoadTime={engagementData?.avgLoadTime || 0}
              trendData={
                engagementData?.trendData.map((d) => ({
                  timestamp: d.timestamp,
                  avgTimeSpent: d.avgTimeSpent,
                  avgLoadTime: d.avgLoadTime,
                })) || []
              }
              isLoading={isLoadingEngagement}
              error={engagementError}
            />
            <UserEngagementGraph
              screenName={decodedScreenName}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              startTime={startTime || undefined}
              endTime={endTime || undefined}
              spanType="screen_session"
            />
            <ActiveSessionsGraph
              screenName={decodedScreenName}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              startTime={startTime || undefined}
              endTime={endTime || undefined}
              spanType="screen_session"
            />
          </SimpleGrid>
        </Tabs.Panel>

        {/* Performance & Stability Tab */}
        <Tabs.Panel value="performance">
          {/* Issue Type Filter */}
          <Box mb="md">
            <VitalsFilters
              issueType={issueType}
              onIssueTypeChange={handleIssueTypeChange}
              stats={vitalsStats}
            />
          </Box>

          {/* Stats Cards - 3 Sections */}
          <Box className={vitalsClasses.statsContainer}>
            <CrashMetricsStats
              startTime={formattedStartTime}
              endTime={formattedEndTime}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              screenName={decodedScreenName}
            />
            <ANRMetricsStats
              startTime={formattedStartTime}
              endTime={formattedEndTime}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              screenName={decodedScreenName}
            />
            {/* Section 3: Performance Metrics */}
            <Box className={vitalsClasses.statSection}>
              <Text className={vitalsClasses.sectionTitle}>Performance</Text>
              <Box className={vitalsClasses.metricsGrid}>
                <Box className={vitalsClasses.statItem}>
                  <Text className={vitalsClasses.statLabel}>
                    Screen Load Time
                  </Text>
                  <Text className={vitalsClasses.statValue} c="teal">
                    {engagementData?.avgLoadTime
                      ? engagementData.avgLoadTime >= 1
                        ? `${engagementData.avgLoadTime.toFixed(1)}s`
                        : `${(engagementData.avgLoadTime * 1000).toFixed(0)}ms`
                      : "0ms"}
                  </Text>
                </Box>
              </Box>
            </Box>
          </Box>

          {/* Trend Graph */}
          {issueType === ISSUE_TYPES.CRASHES && (
            <CrashTrendGraph
              startTime={formattedStartTime}
              endTime={formattedEndTime}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              screenName={decodedScreenName}
              title={graphConfig.title}
              lineColor={graphConfig.color}
            />
          )}
          {issueType === ISSUE_TYPES.ANRS && (
            <ANRTrendGraph
              startTime={formattedStartTime}
              endTime={formattedEndTime}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              screenName={decodedScreenName}
              title={graphConfig.title}
              lineColor={graphConfig.color}
            />
          )}
          {issueType === ISSUE_TYPES.NON_FATALS && (
            <NonFatalTrendGraph
              startTime={formattedStartTime}
              endTime={formattedEndTime}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              screenName={decodedScreenName}
              title={graphConfig.title}
              lineColor={graphConfig.color}
            />
          )}

          {/* Issues List - Single view based on selected tab */}
          {issueType === ISSUE_TYPES.CRASHES && (
            <CrashList
              startTime={formattedStartTime}
              endTime={formattedEndTime}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              screenName={decodedScreenName}
            />
          )}
          {issueType === ISSUE_TYPES.ANRS && (
            <ANRList
              startTime={formattedStartTime}
              endTime={formattedEndTime}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              screenName={decodedScreenName}
            />
          )}
          {issueType === ISSUE_TYPES.NON_FATALS && (
            <NonFatalList
              startTime={formattedStartTime}
              endTime={formattedEndTime}
              appVersion={appVersion !== "all" ? appVersion : undefined}
              osVersion={osVersion !== "all" ? osVersion : undefined}
              device={device !== "all" ? device : undefined}
              screenName={decodedScreenName}
            />
          )}
        </Tabs.Panel>

        {/* Network Tab */}
        <Tabs.Panel value="network">
          <NetworkList
            screenName={decodedScreenName}
            showHeader={false}
            showFilters={false}
            externalStartTime={startTime}
            externalEndTime={endTime}
            externalFilters={useMemo(() => {
              const filters: Array<{
                field: string;
                operator: "LIKE" | "EQ";
                value: string[];
              }> = [];

              if (appVersion !== "all") {
                filters.push({
                  field: "ResourceAttributes['app.version']",
                  operator: "EQ" as const,
                  value: [appVersion],
                });
              }

              if (osVersion !== "all") {
                filters.push({
                  field: "ResourceAttributes['os.version']",
                  operator: "EQ" as const,
                  value: [osVersion],
                });
              }

              if (device !== "all") {
                filters.push({
                  field: "ResourceAttributes['device.model']",
                  operator: "EQ" as const,
                  value: [device],
                });
              }

              return filters;
            }, [appVersion, osVersion, device])}
          />
        </Tabs.Panel>
      </div>
    </Tabs>
  );
}
