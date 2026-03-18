import { Box, Button, Group, Skeleton, Stack, Text } from "@mantine/core";
import { IconPlayerPlay, IconRefresh } from "@tabler/icons-react";
import dayjs from "dayjs";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { useGetRcaReport } from "../../../../hooks/useGetRcaReport/useGetRcaReport";
import { SessionCard } from "./components";
import { ROOT_CAUSE_MESSAGES } from "./RootCause.constants";
import type { RootCauseProps } from "./RootCause.interface";
import { RcaReportView } from "./RcaReportView";
import classes from "./RootCause.module.css";

/** Mock related session replays for the RCA view; IDs match Session Replay mock (sess_mock_001, sess_mock_002) for Watch Replay navigation */
const MOCK_RELATED_SESSIONS = [
  {
    sessionId: "sess_mock_001",
    duration: "2:34",
    relativeTime: "30 min ago",
    device: "SM-S911B • Android 13",
    failureSummary:
      "Payment gateway timeout at 8.2s → app crashed (OOM during retry)",
  },
  {
    sessionId: "sess_mock_002",
    duration: "1:48",
    relativeTime: "2 hours ago",
    device: "iPhone 15 Pro • iOS 17.4",
    failureSummary:
      "Payment screen ANR for 6s on Airtel 4G — user force-closed app",
  },
];

export const RootCause = ({
  interactionName,
  date,
  projectId,
}: RootCauseProps) => {
  const effectiveProjectId = projectId?.trim() ?? "";
  const hasProjectId = effectiveProjectId !== "";
  const hasInteractionName = !!interactionName?.trim();

  const {
    data: reportResponse,
    isLoading: reportLoading,
    isError: reportError,
    refetch: refetchReport,
    error: reportErrorDetail,
  } = useGetRcaReport({
    interactionName,
    date: date ?? null,
    enabled: hasInteractionName && hasProjectId,
    projectId: hasProjectId ? effectiveProjectId : null,
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
