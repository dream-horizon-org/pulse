import { Box, Button, Skeleton, Stack, Text } from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useMemo } from "react";
import { useLocation } from "react-router-dom";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { useGetInteractionDetailsGraphs } from "../../../../hooks/useGetInteractionDetailsGraphs";
import { useGetRcaReport } from "../../../../hooks/useGetRcaReport/useGetRcaReport";
import type { RcaReportTenantContext } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import type { EvidenceCardProps } from "./components";
import { EvidenceStrip } from "./components";
import { getRootCauseMockLinkedFunnelsJourneys } from "./rootCauseMockLinkedFunnelsJourneys";
import { getRootCauseMockRelatedSessions } from "./rootCauseMockRelatedSessions";
import { buildScreenHeatmapUrl, getRcaHeatmapTargets } from "./rcaHeatmapLinks";
import { RcaReportView } from "./RcaReportView";
import { ROOT_CAUSE_MESSAGES } from "./RootCause.constants";
import type { RootCauseProps } from "./RootCause.interface";
import classes from "./RootCause.module.css";

export const RootCause = ({
  interactionName,
  date,
  projectId,
  startTime,
  endTime,
  dashboardFilters,
}: RootCauseProps) => {
  const location = useLocation();
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
    const relatedSessions = getRootCauseMockRelatedSessions();
    const linkedFunnelsJourneys = getRootCauseMockLinkedFunnelsJourneys();
    const evidenceItems: EvidenceCardProps[] = [
      ...relatedSessions.map((s) => ({
        type: "session-replay" as const,
        name: s.sessionId,
        timestamp: s.relativeTime,
        subtitle: `${s.duration} · ${s.device}`,
        detail: s.failureSummary,
        href: `/projects/${effectiveProjectId}/session-replay/${s.sessionId}`,
      })),
      ...linkedFunnelsJourneys.map((item) => {
        const tagLine =
          item.tags.length > 0
            ? item.tags.slice(0, 4).join(" · ")
            : `Created by ${item.createdBy}`;
        return {
          type: (item.type === "FUNNEL" ? "funnel" : "journey") as
            | "funnel"
            | "journey",
          name: item.name,
          timestamp: item.createdAt,
          subtitle: tagLine,
          detail: item.description,
          href: `/projects/${effectiveProjectId}/funnels-journeys/${item.id}`,
        };
      }),
      ...rcaHeatmapTargets.map((target) => ({
        type: "heatmap" as const,
        name: target.screenName,
        subtitle: target.label,
        detail:
          interactionName === "JoinContestButtonClick"
            ? "Tap, rage, and dead-zone density for this window—cross-check with RCA tail latency (e.g. P95 ~6.87s on Android 4.0.0 + OS 13, ~5.1s on iOS 4.2.0) and error hotspots on Join rows."
            : "Tap and gesture density on this screen for the dashboard time range.",
        href: buildScreenHeatmapUrl(
          effectiveProjectId,
          target.screenName,
          startTime,
          endTime,
          reportPayload.heatmap_signal_quality,
          `${location.pathname}${location.search}`,
        ),
      })),
    ];
    return (
      <>
        <RcaReportView
          report={report}
          rcaInsights={reportPayload.rca_insights}
          cached={reportPayload.cached}
          cachedAt={cachedAtFormatted}
        />
        <EvidenceStrip items={evidenceItems} />
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
