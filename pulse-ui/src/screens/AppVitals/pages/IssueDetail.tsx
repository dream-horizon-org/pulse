import { useState, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Box, Text, Button, Paper, Group } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import {
  IssueDetailsCard,
  OccurrenceSection,
  StackTraceSection,
} from "../components";
import { getXAxisInterval } from "../helpers/trendDataHelper";
import {
  useIssueDetailData,
  useIssueStackTraces,
  useIssueScreenBreakdown,
  useIssueTrendData,
} from "./hooks";
import { SkeletonLoader, ChartSkeleton } from "../../../components/Skeletons";
import dayjs from "dayjs";
import classes from "./IssueDetail.module.css";

const CHART_COLORS = {
  appVersion: ["#14b8a6", "#06b6d4", "#8b5cf6", "#f59e0b"],
  os: ["#10b981", "#3b82f6", "#8b5cf6", "#ef4444"],
};

export const IssueDetail: React.FC = () => {
  const { groupId } = useParams<{ groupId: string }>();
  const navigate = useNavigate();

  const [trendView, setTrendView] = useState("aggregated");
  
  // Use default time range (hooks will use last 7 days if empty)
  const startTime = "";
  const endTime = "";

  // Fetch issue details from API
  const { issue, queryState: issueQueryState } = useIssueDetailData({
    groupId: groupId || "",
    startTime,
    endTime,
  });

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
    startTime,
    endTime,
    limit: 10,
  });

  // Fetch screen breakdown
  const { screenBreakdown } = useIssueScreenBreakdown({
    groupId: groupId || "",
    startTime,
    endTime,
  });

  // Format time for API calls
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

  // Fetch trend data from API
  const { trendData } = useIssueTrendData({
    groupId: groupId || "",
    startTime: formattedStartTime,
    endTime: formattedEndTime,
    trendView,
    appVersion: "all",
    osVersion: "all",
    device: "all",
  });


  // Loading state - show skeleton layout matching actual content
  if (issueQueryState.isLoading) {
    return (
      <Box className={classes.pageContainer}>
        {/* Back Button Skeleton */}
        <SkeletonLoader height={32} width={160} radius="md" className={classes.backButton} />

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
            onClick={() => navigate("/app-vitals")}
          >
            Go Back to App Vitals
          </Button>
        </Paper>
      </Box>
    );
  }

  // Not found state
  if (!issue) {
    return (
      <Box className={classes.pageContainer}>
        <Paper className={classes.notFoundCard}>
          <Text className={classes.notFoundTitle}>Issue not found</Text>
          <Text className={classes.notFoundText}>
            The issue you're looking for doesn't exist or has been removed.
          </Text>
          <Button
            variant="light"
            color="teal"
            mt="md"
            onClick={() => navigate("/app-vitals")}
          >
            Go Back to App Vitals
          </Button>
        </Paper>
      </Box>
    );
  }

  return (
    <Box className={classes.pageContainer}>
      {/* Back Button */}
      <Button
        variant="subtle"
        color="teal"
        leftSection={<IconArrowLeft size={16} />}
        onClick={() => navigate("/app-vitals")}
        className={classes.backButton}
      >
        Back to App Vitals
      </Button>

      {/* Issue Details */}
      <IssueDetailsCard issue={issue} issueType={issueType} />

      {/* Occurrence Section */}
      <OccurrenceSection
        trendView={trendView}
        onTrendViewChange={setTrendView}
        trendData={trendData}
        screenBreakdown={screenBreakdown}
        chartColors={CHART_COLORS}
        getXAxisInterval={() => getXAxisInterval(startTime, endTime)}
      />

      {/* Stack Trace Section */}
      <StackTraceSection stackTraces={stackTraces || []} />
    </Box>
  );
};
