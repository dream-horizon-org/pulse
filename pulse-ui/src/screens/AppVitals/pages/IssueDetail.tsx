import { useState, useMemo, useEffect, useCallback } from "react";
import {
  useParams,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import { Box, Text, Button, Paper, Group } from "@mantine/core";
import { IconArrowLeft, IconCalendarOff } from "@tabler/icons-react";
import {
  IssueDetailsCard,
  OccurrenceSection,
  StackTraceSection,
} from "../components";
import { getXAxisInterval } from "../helpers/trendDataHelper";
import { useExceptionTimestamps } from "../components/ExceptionTable/hooks/useExceptionTimestamps";
import {
  useIssueDetailData,
  useIssueStackTraces,
  useIssueScreenBreakdown,
  useIssueTrendData,
} from "./hooks";
import { SkeletonLoader, ChartSkeleton } from "../../../components/Skeletons";
import dayjs from "dayjs";
import classes from "./IssueDetail.module.css";
import { useFilterStore } from "../../../stores/useFilterStore";
import { getStartAndEndDateTimeString } from "../../../utils/DateUtil";
import {
  DEFAULT_QUICK_TIME_FILTER,
  DEFAULT_QUICK_TIME_FILTER_INDEX,
  CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS,
} from "../../../constants";
import { StartEndDateTimeType } from "../../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import DateTimeRangePicker from "../../CriticalInteractionDetails/components/DateTimeRangePicker/DateTimeRangePicker";

const CHART_COLORS = {
  appVersion: ["#14b8a6", "#06b6d4", "#8b5cf6", "#f59e0b"],
  os: ["#10b981", "#3b82f6", "#8b5cf6", "#ef4444"],
};

export const IssueDetail: React.FC = () => {
  const { groupId, projectId } = useParams<{
    groupId: string;
    projectId: string;
  }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const {
    startTime: storeStartTime,
    endTime: storeEndTime,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    handleTimeFilterChange: storeHandleTimeFilterChange,
    handleDateTimeReset,
    initializeFromUrlParams,
    selectedTimeFilter,
  } = useFilterStore();

  const [trendView, setTrendView] = useState("aggregated");

  const getDefaultTimeRange = () =>
    getStartAndEndDateTimeString(DEFAULT_QUICK_TIME_FILTER, 2);

  const startTime = useMemo(
    () => storeStartTime || getDefaultTimeRange().startDate,
    [storeStartTime],
  );

  const endTime = useMemo(
    () => storeEndTime || getDefaultTimeRange().endDate,
    [storeEndTime],
  );

  // Single canonical UTC range for every EXCEPTIONS query on this page (matches DateTimeRangePicker / store)
  const formattedStartTime = useMemo(() => {
    if (!startTime) return "";
    try {
      if (startTime.includes("T") || startTime.includes("Z")) {
        return dayjs.utc(startTime).toISOString();
      }
      return dayjs.utc(startTime, "YYYY-MM-DD HH:mm:ss").toISOString();
    } catch {
      return "";
    }
  }, [startTime]);

  const formattedEndTime = useMemo(() => {
    if (!endTime) return "";
    try {
      if (endTime.includes("T") || endTime.includes("Z")) {
        return dayjs.utc(endTime).toISOString();
      }
      return dayjs.utc(endTime, "YYYY-MM-DD HH:mm:ss").toISOString();
    } catch {
      return "";
    }
  }, [endTime]);

  const issueDetailRangeStart = formattedStartTime || startTime;
  const issueDetailRangeEnd = formattedEndTime || endTime;

  useEffect(() => {
    initializeFromUrlParams(searchParams);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const handleTimeFilterChange = useCallback(
    (value: StartEndDateTimeType) => {
      storeHandleTimeFilterChange(value);
    },
    [storeHandleTimeFilterChange],
  );

  const handleResetTimeRange = useCallback(() => {
    const newSearchParams = handleDateTimeReset(
      CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS,
      DEFAULT_QUICK_TIME_FILTER_INDEX,
      2,
      searchParams,
      handleTimeFilterChange,
    );
    setSearchParams(newSearchParams);
  }, [
    handleDateTimeReset,
    searchParams,
    handleTimeFilterChange,
    setSearchParams,
  ]);

  const showResetTimeRange = quickTimeRangeFilterIndex === -1;

  const navigateToAppVitalsList = useCallback(() => {
    const qs = searchParams.toString();
    const base = `/projects/${projectId}/app-vitals`;
    navigate(qs ? `${base}?${qs}` : base);
  }, [navigate, projectId, searchParams]);

  // Fetch issue details from API
  const { issue, queryState: issueQueryState } = useIssueDetailData({
    groupId: groupId || "",
    startTime: issueDetailRangeStart,
    endTime: issueDetailRangeEnd,
  });

  /** Same 6-month min/max window as the listing (`useExceptionTimestamps`), not filter-scoped. */
  const eventNameForTimestamps = useMemo((): string | undefined => {
    if (!issue?.pulseEventName) return undefined;
    const n = issue.pulseEventName;
    if (n === "device.crash") return "device.crash";
    if (n === "device.anr") return "device.anr";
    return undefined;
  }, [issue]);

  const groupIdsForTimestamps = useMemo(
    () => (groupId && issue ? [groupId] : []),
    [groupId, issue],
  );

  const { timestampsMap } = useExceptionTimestamps({
    groupIds: groupIdsForTimestamps,
    appVersion: "all",
    osVersion: "all",
    device: "all",
    eventName: eventNameForTimestamps,
  });

  const issueForDetailsCard = useMemo(() => {
    if (!issue) return null;
    if (!groupId) return issue;
    const ts = timestampsMap.get(groupId);
    if (ts?.firstSeen && ts?.lastSeen) {
      return { ...issue, firstSeen: ts.firstSeen, lastSeen: ts.lastSeen };
    }
    return issue;
  }, [issue, timestampsMap, groupId]);

  // Determine issue type
  const issueType = useMemo(() => {
    if (!issue) return "Issue";
    if (issue.id.startsWith("crash")) return "Crash";
    if (issue.id.startsWith("anr")) return "ANR";
    if (issue.id.startsWith("nonfatal")) return "Non-Fatal";
    return "Issue";
  }, [issue]);

  // Fetch stack traces (occurrences)
  const { stackTraces } = useIssueStackTraces({
    groupId: groupId || "",
    startTime: issueDetailRangeStart,
    endTime: issueDetailRangeEnd,
    limit: 10,
  });

  // Fetch screen breakdown
  const { screenBreakdown } = useIssueScreenBreakdown({
    groupId: groupId || "",
    startTime: issueDetailRangeStart,
    endTime: issueDetailRangeEnd,
  });

  // Fetch trend data from API
  const {
    trendData,
    timeRange: issueTrendTimeRange,
    bucketSize: issueTrendBucketSize,
  } = useIssueTrendData({
    groupId: groupId || "",
    startTime: issueDetailRangeStart,
    endTime: issueDetailRangeEnd,
    trendView,
    appVersion: "all",
    osVersion: "all",
    device: "all",
  });

  // Loading state - show skeleton layout matching actual content
  if (issueQueryState.isLoading) {
    return (
      <Box className={classes.pageContainer}>
        {/* Toolbar: back + time filter skeleton */}
        <Group justify="space-between" align="center" wrap="wrap" className={classes.pageToolbar}>
          <SkeletonLoader height={32} width={160} radius="md" />
          <SkeletonLoader height={36} width={280} radius="md" />
        </Group>

        {/* Issue Details Card Skeleton */}
        <Paper className={classes.issueCardSkeleton}>
          <Group justify="space-between" align="center" wrap="nowrap">
            <Group gap="md" align="center" style={{ flex: 1 }}>
              <SkeletonLoader height={20} width={20} radius="sm" />
              <SkeletonLoader height={24} width={60} radius="md" />
              <SkeletonLoader height={18} width="40%" radius="sm" />
            </Group>
            <Group gap="xl">
              <SkeletonLoader height={20} width={100} radius="sm" />
              <SkeletonLoader height={20} width={80} radius="sm" />
              <SkeletonLoader height={20} width={100} radius="sm" />
            </Group>
          </Group>
        </Paper>

        {/* Occurrence Section Skeleton */}
        <Paper className={classes.sectionSkeleton}>
          <SkeletonLoader height={18} width={100} radius="sm" />
          <Box mt="md">
            <SkeletonLoader height={36} width={320} radius="md" />
          </Box>
          <Box mt="md">
            <ChartSkeleton height={280} />
          </Box>
        </Paper>

        {/* Stack Trace Section Skeleton */}
        <Paper className={classes.sectionSkeleton}>
          <Group justify="space-between" align="center" mb="md">
            <SkeletonLoader height={18} width={100} radius="sm" />
            <Group gap="sm">
              <SkeletonLoader height={28} width={28} radius="sm" />
              <SkeletonLoader height={16} width={140} radius="sm" />
              <SkeletonLoader height={28} width={28} radius="sm" />
            </Group>
          </Group>
          <Paper className={classes.traceHeaderSkeleton}>
            <Group gap="lg">
              <SkeletonLoader height={14} width={120} radius="sm" />
              <SkeletonLoader height={14} width={100} radius="sm" />
              <SkeletonLoader height={14} width={80} radius="sm" />
            </Group>
            <SkeletonLoader height={24} width={100} radius="md" />
          </Paper>
          <Box className={classes.traceContentSkeleton}>
            <SkeletonLoader height={12} width="90%" radius="xs" />
            <SkeletonLoader height={12} width="85%" radius="xs" />
            <SkeletonLoader height={12} width="70%" radius="xs" />
            <SkeletonLoader height={12} width="95%" radius="xs" />
            <SkeletonLoader height={12} width="60%" radius="xs" />
            <SkeletonLoader height={12} width="80%" radius="xs" />
          </Box>
        </Paper>
      </Box>
    );
  }

  // Error state
  if (issueQueryState.isError) {
    return (
      <Box className={classes.pageContainer}>
        <Paper className={classes.notFoundCard}>
          <Text className={classes.notFoundTitle}>Error loading issue</Text>
          <Text className={classes.notFoundText}>
            {issueQueryState.errorMessage || "Failed to load issue details"}
          </Text>
          <Button
            variant="light"
            color="teal"
            mt="md"
            onClick={navigateToAppVitalsList}
          >
            Go Back to App Vitals
          </Button>
        </Paper>
      </Box>
    );
  }

  // Invalid route (no group id)
  if (!groupId) {
    return (
      <Box className={classes.pageContainer}>
        <Paper className={classes.notFoundCard}>
          <Text className={classes.notFoundTitle}>Issue not found</Text>
          <Text className={classes.notFoundText}>
            The issue you&apos;re looking for doesn&apos;t exist or has been
            removed.
          </Text>
          <Button
            variant="light"
            color="teal"
            mt="md"
            onClick={navigateToAppVitalsList}
          >
            Go Back to App Vitals
          </Button>
        </Paper>
      </Box>
    );
  }

  // No rows for this GroupId in the selected time range (query succeeded)
  if (!issue) {
    return (
      <Box className={classes.pageContainer}>
        <Group
          justify="space-between"
          align="center"
          wrap="wrap"
          gap="md"
          className={classes.pageToolbar}
        >
          <Button
            variant="subtle"
            color="teal"
            leftSection={<IconArrowLeft size={16} />}
            onClick={navigateToAppVitalsList}
            className={classes.backButton}
          >
            Back to App Vitals
          </Button>
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
            defaultEndTime={selectedTimeFilter?.endDate || endTime}
            defaultStartTime={selectedTimeFilter?.startDate || startTime}
            showRefreshButton={true}
          />
        </Group>

        <Paper className={classes.emptyRangeCard}>
          <IconCalendarOff
            size={40}
            stroke={1.5}
            color="var(--mantine-color-teal-6)"
            style={{ marginBottom: 12 }}
          />
          <Text className={classes.emptyRangeTitle}>
            No events in this time range
          </Text>
          <Text className={classes.emptyRangeText}>
            This issue has no occurrences between the selected start and end
            times. Try widening the range above, or go back to App Vitals to
            pick a different issue.
          </Text>
          <Button
            variant="light"
            color="teal"
            mt="md"
            onClick={navigateToAppVitalsList}
          >
            Go Back to App Vitals
          </Button>
        </Paper>
      </Box>
    );
  }

  return (
    <Box className={classes.pageContainer}>
      <Group
        justify="space-between"
        align="center"
        wrap="wrap"
        gap="md"
        className={classes.pageToolbar}
      >
        <Button
          variant="subtle"
          color="teal"
          leftSection={<IconArrowLeft size={16} />}
          onClick={navigateToAppVitalsList}
          className={classes.backButton}
        >
          Back to App Vitals
        </Button>
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
          defaultEndTime={selectedTimeFilter?.endDate || endTime}
          defaultStartTime={selectedTimeFilter?.startDate || startTime}
          showRefreshButton={true}
        />
      </Group>

      {/* Issue Details */}
      <IssueDetailsCard
        issue={issueForDetailsCard ?? issue}
        issueType={issueType}
        groupId={groupId || ""}
      />

      {/* Occurrence Section */}
      <OccurrenceSection
        trendView={trendView}
        onTrendViewChange={setTrendView}
        trendData={trendData}
        screenBreakdown={screenBreakdown}
        chartColors={CHART_COLORS}
        bucketSize={issueTrendBucketSize}
        rangeStart={issueTrendTimeRange.start}
        rangeEnd={issueTrendTimeRange.end}
        getXAxisInterval={() =>
          getXAxisInterval(issueTrendTimeRange.start, issueTrendTimeRange.end)
        }
        onTimeFilterChange={handleTimeFilterChange}
        showResetTimeRange={showResetTimeRange}
        onResetTimeRange={handleResetTimeRange}
      />

      {/* Stack Trace Section */}
      <StackTraceSection
        stackTraces={stackTraces || []}
        totalOccurrences={issue?.occurrences}
      />
    </Box>
  );
};
