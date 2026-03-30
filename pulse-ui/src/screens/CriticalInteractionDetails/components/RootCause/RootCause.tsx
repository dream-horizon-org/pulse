import { Box, Button, Group, Skeleton, Stack, Text } from "@mantine/core";
import {
  IconChartFunnel,
  IconLayoutGrid,
  IconPlayerPlay,
  IconRefresh,
} from "@tabler/icons-react";
import dayjs from "dayjs";
import { useMemo } from "react";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { useGetInteractionDetailsGraphs } from "../../../../hooks/useGetInteractionDetailsGraphs";
import { useGetRcaReport } from "../../../../hooks/useGetRcaReport/useGetRcaReport";
import type { RcaReportTenantContext } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import { FunnelJourneyCard, HeatmapRcaCard, SessionCard } from "./components";
import { buildScreenHeatmapUrl, getRcaHeatmapTargets } from "./rcaHeatmapLinks";
import { RcaReportView } from "./RcaReportView";
import { ROOT_CAUSE_MESSAGES } from "./RootCause.constants";
import type { RootCauseProps } from "./RootCause.interface";
import classes from "./RootCause.module.css";

/** Mock related session replays for the RCA view; IDs match `mockSessionReplayScenarios` (same mock replay frames as other sess_* mocks). */
const MOCK_RELATED_SESSIONS = [
  {
    sessionId: "sess_rca_join_mock_001",
    duration: "3:18",
    relativeTime: "45 min ago",
    device: "Android 13 · App 4.0.0 · Pixel 8",
    failureSummary:
      "Join API error, then ANR on retry — matches Android 4.0.0 + OS 13 RCA segment.",
  },
  {
    sessionId: "sess_rca_join_mock_002",
    duration: "4:05",
    relativeTime: "1 hr ago",
    device: "iOS 17.4 · App 4.2.0 · iPhone 15 Pro",
    failureSummary:
      "Slow join + interaction error — matches iOS 4.2.0 RCA segment.",
  },
];

/** Mock linked funnels and journeys for the RCA view */
const MOCK_LINKED_FUNNELS_JOURNEYS = [
  {
    id: "funnel-payment-001",
    name: "Payment Flow Conversion",
    type: "FUNNEL" as const,
    status: "ACTIVE" as const,
    createdBy: "sarah@example.com",
    createdAt: "3 days ago",
    tags: ["payment", "conversion", "critical"],
    description:
      "Tracks user conversion through the payment process including checkout and order completion.",
  },
  {
    id: "journey-onboarding-001",
    name: "User Onboarding Journey",
    type: "JOURNEY" as const,
    status: "ACTIVE" as const,
    createdBy: "alex@example.com",
    createdAt: "1 week ago",
    tags: ["onboarding", "ux", "retention"],
    description:
      "Maps the complete user journey from app launch to account creation and first purchase.",
  },
];

export const RootCause = ({
  interactionName,
  date,
  projectId,
  startTime,
  endTime,
  dashboardFilters,
}: RootCauseProps) => {
  const effectiveProjectId = projectId?.trim() ?? "";
  const hasProjectId = effectiveProjectId !== "";
  const hasInteractionName = !!interactionName?.trim();
  const hasTimeRange = !!(
    startTime &&
    endTime &&
    String(startTime).trim() !== "" &&
    String(endTime).trim() !== ""
  );

  const { metrics, isLoading: overviewGraphsLoading } =
    useGetInteractionDetailsGraphs({
      interactionName: interactionName ?? undefined,
      startTime: startTime ?? "",
      endTime: endTime ?? "",
      enabled: hasInteractionName && hasTimeRange,
      dashboardFilters: dashboardFilters ?? undefined,
    });

  const tenantContextForRca = useMemo((): RcaReportTenantContext | null => {
    if (!metrics.hasData) return null;
    if (metrics.errorRate == null || metrics.poorUsersPercentage == null) {
      return null;
    }
    const poor = parseFloat(
      String(metrics.poorUsersPercentage).replace(/%/g, ""),
    );
    if (Number.isNaN(poor)) return null;
    const ctx: RcaReportTenantContext = {
      errorRatePercent: metrics.errorRate,
      poorUsersPercent: poor,
    };
    if (metrics.apdex != null) ctx.apdex = metrics.apdex;
    if (metrics.p50 != null) ctx.p50Ms = metrics.p50;
    if (metrics.p95 != null) ctx.p95Ms = metrics.p95;
    return ctx;
  }, [metrics]);

  const rcaHeatmapTargets = useMemo(
    () => getRcaHeatmapTargets(interactionName),
    [interactionName],
  );

  const rcaQueryEnabled =
    hasInteractionName &&
    hasProjectId &&
    (!hasTimeRange || !overviewGraphsLoading);

  const {
    data: reportResponse,
    isLoading: reportLoading,
    isError: reportError,
    refetch: refetchReport,
    error: reportErrorDetail,
  } = useGetRcaReport({
    interactionName,
    date: date ?? null,
    enabled: rcaQueryEnabled,
    projectId: hasProjectId ? effectiveProjectId : null,
    tenantContext: tenantContextForRca,
  });

  const reportPayload = reportResponse?.data ?? null;
  const hasReportStructure = reportPayload?.report != null;
  const hasNonEmptyInsights =
    reportPayload?.rca_insights != null &&
    String(reportPayload.rca_insights).trim() !== "";
  const showReport =
    reportResponse?.status === 200 &&
    reportPayload != null &&
    (hasReportStructure || hasNonEmptyInsights);

  if (!hasInteractionName) {
    return (
      <Box className={classes.container}>
        <Text className={classes.stateMessage}>
          {ROOT_CAUSE_MESSAGES.NO_DATA}
        </Text>
      </Box>
    );
  }

  if (!hasProjectId) {
    return (
      <Box className={classes.container}>
        <Text className={classes.stateMessage}>
          {ROOT_CAUSE_MESSAGES.PROJECT_REQUIRED}
        </Text>
      </Box>
    );
  }

  if (hasTimeRange && overviewGraphsLoading) {
    return (
      <Box className={classes.container}>
        <div className={classes.skeletonWrapper}>
          <Skeleton height={24} width={200} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} />
        </div>
      </Box>
    );
  }

  if (reportLoading) {
    return (
      <Box className={classes.container}>
        <div className={classes.skeletonWrapper}>
          <Skeleton height={24} width={200} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} />
        </div>
      </Box>
    );
  }

  if (showReport && reportPayload) {
    const cachedAtFormatted = reportPayload.cached
      ? dayjs().format("MMM D, YYYY [at] h:mm A")
      : null;
    const report = reportPayload.report ?? {
      markdown: null,
      charts: [],
      tables: [],
    };
    const relatedSessions = MOCK_RELATED_SESSIONS;
    const linkedFunnelsJourneys = MOCK_LINKED_FUNNELS_JOURNEYS;
    return (
      <>
        <RcaReportView
          report={report}
          rcaInsights={reportPayload.rca_insights}
          cached={reportPayload.cached}
          cachedAt={cachedAtFormatted}
        />
        {relatedSessions.length > 0 && (
          <section
            className={classes.relatedReplaysSection}
            aria-label="Related session replays"
          >
            <Group className={classes.relatedReplaysHeader} gap="xs">
              <IconPlayerPlay
                size={18}
                color="var(--mantine-color-teal-7)"
                aria-hidden
              />
              <Text className={classes.relatedReplaysTitle}>
                Related Session Replays
              </Text>
              <Box component="span" className={classes.relatedReplaysBadge}>
                {relatedSessions.length}
              </Box>
            </Group>
            <div className={classes.relatedReplaysGrid}>
              {relatedSessions.map((session) => (
                <SessionCard
                  key={session.sessionId}
                  sessionId={session.sessionId}
                  duration={session.duration}
                  relativeTime={session.relativeTime}
                  device={session.device}
                  failureSummary={session.failureSummary}
                  replayUrl={`/projects/${effectiveProjectId}/session-replay/${session.sessionId}`}
                />
              ))}
            </div>
          </section>
        )}
        {linkedFunnelsJourneys.length > 0 && (
          <section
            className={classes.linkedFunnelsJourneysSection}
            aria-label="Linked funnels and journeys"
          >
            <Group className={classes.linkedFunnelsJourneysHeader} gap="xs">
              <IconChartFunnel
                size={18}
                color="var(--mantine-color-teal-7)"
                aria-hidden
              />
              <Text className={classes.linkedFunnelsJourneysTitle}>
                Linked Funnels & Journeys
              </Text>
              <Box
                component="span"
                className={classes.linkedFunnelsJourneysBadge}
              >
                {linkedFunnelsJourneys.length}
              </Box>
            </Group>
            <div className={classes.linkedFunnelsJourneysGrid}>
              {linkedFunnelsJourneys.map((item) => (
                <FunnelJourneyCard
                  key={item.id}
                  id={item.id}
                  name={item.name}
                  type={item.type}
                  status={item.status}
                  createdBy={item.createdBy}
                  createdAt={item.createdAt}
                  tags={item.tags}
                  description={item.description}
                  detailUrl={`/projects/${effectiveProjectId}/funnels-journeys/${item.id}`}
                />
              ))}
            </div>
          </section>
        )}
        {rcaHeatmapTargets.length > 0 && (
          <section
            className={classes.relatedHeatmapsSection}
            aria-label="Related heatmaps"
          >
            <Group className={classes.relatedReplaysHeader} gap="xs">
              <IconLayoutGrid
                size={18}
                color="var(--mantine-color-teal-7)"
                aria-hidden
              />
              <Text className={classes.relatedReplaysTitle}>
                Related heatmaps
              </Text>
              <Box component="span" className={classes.relatedReplaysBadge}>
                {rcaHeatmapTargets.length}
              </Box>
            </Group>
            <div className={classes.relatedReplaysGrid}>
              {rcaHeatmapTargets.map((target, idx) => (
                <HeatmapRcaCard
                  key={`${target.screenName}-${idx}`}
                  screenName={target.screenName}
                  label={target.label}
                  heatmapUrl={buildScreenHeatmapUrl(
                    effectiveProjectId,
                    target.screenName,
                    startTime,
                    endTime,
                  )}
                />
              ))}
            </div>
          </section>
        )}
      </>
    );
  }

  const status = reportResponse?.status ?? 0;
  const is404 = status === 404;

  if (reportError || status === 0) {
    const message =
      reportErrorDetail?.message ??
      reportResponse?.error?.message ??
      ROOT_CAUSE_MESSAGES.REQUEST_FAILED;
    const messageLower = message.toLowerCase();
    const isTimeout =
      messageLower.includes("timeout") || message === "Request Timeout";
    const displayMessage = isTimeout
      ? ROOT_CAUSE_MESSAGES.REQUEST_TIMEOUT
      : message;
    return (
      <Box className={classes.container}>
        <Stack align="center" gap="md" className={classes.stateMessage}>
          <ErrorAndEmptyState
            message={displayMessage}
            classes={[classes.errorState]}
          />
          <Button
            className={classes.retryButton}
            leftSection={<IconRefresh size={16} />}
            variant="light"
            onClick={() => refetchReport()}
          >
            Retry
          </Button>
        </Stack>
      </Box>
    );
  }

  if (is404) {
    return (
      <Box className={classes.container}>
        <Stack align="center" gap="md" className={classes.stateMessage}>
          <ErrorAndEmptyState
            message={ROOT_CAUSE_MESSAGES.FEATURE_OR_NO_DATA}
            classes={[classes.errorState]}
          />
          <Button
            className={classes.retryButton}
            leftSection={<IconRefresh size={16} />}
            variant="light"
            onClick={() => refetchReport()}
          >
            Retry
          </Button>
        </Stack>
      </Box>
    );
  }

  const isClientError = status >= 400 && status < 500;
  const isServerError = status >= 500;
  if (isClientError || isServerError) {
    const message =
      reportResponse?.error?.message ?? ROOT_CAUSE_MESSAGES.REQUEST_FAILED;
    return (
      <Box className={classes.container}>
        <Stack align="center" gap="md" className={classes.stateMessage}>
          <ErrorAndEmptyState
            message={message}
            classes={[classes.errorState]}
          />
          <Button
            className={classes.retryButton}
            leftSection={<IconRefresh size={16} />}
            variant="light"
            onClick={() => refetchReport()}
          >
            Retry
          </Button>
        </Stack>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      <Text className={classes.stateMessage}>
        {ROOT_CAUSE_MESSAGES.NO_DATA}
      </Text>
    </Box>
  );
};
