import { Alert, Box, Button, Stack, Text } from "@mantine/core";
import { LoaderWithMessage } from "../../../../components/LoaderWithMessage";
import { IconRefresh } from "@tabler/icons-react";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import utc from "dayjs/plugin/utc";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { getJobIdFromRcaPostResponse } from "../../../../helpers/rcaResponseUnwrap";
import { useGetRcaReport } from "../../../../hooks/useGetRcaReport/useGetRcaReport";
import {
  extractStructuredReport,
  isRcaStructuredReportV1WithContent,
} from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import { useRegenerateRcaReport } from "../../../../hooks/useRegenerateRcaReport/useRegenerateRcaReport";
import { ROOT_CAUSE_MESSAGES } from "./RootCause.constants";
import type { RootCauseProps } from "./RootCause.interface";
import { RcaReportView } from "./RcaReportView";
import classes from "./RootCause.module.css";

dayjs.extend(utc);
dayjs.extend(relativeTime);

const RCA_HTTP_STATUS = {
  OK: 200,
  ACCEPTED: 202,
  NOT_FOUND: 404,
  BAD_GATEWAY: 502,
  SERVICE_UNAVAILABLE: 503,
} as const;

const REGENERATE_DEBOUNCE_MS = 500;

function formatRcaReportCachedAt(
  iso: string | null | undefined,
): string | null {
  if (iso == null || String(iso).trim() === "") return null;
  const parsed = dayjs(iso);
  return parsed.isValid() ? parsed.format("MMM D, YYYY [at] h:mm A") : null;
}

function formatRcaReportGeneratedAgo(
  iso: string | null | undefined,
): string | null {
  if (iso == null || String(iso).trim() === "") return null;
  const parsed = dayjs(iso);
  return parsed.isValid() ? parsed.fromNow() : null;
}

export function RootCause({
  interactionName,
  date,
  projectId,
}: RootCauseProps) {
  const regenerateDebounceTimerRef = useRef<number | null>(null);
  const [rcaRequestSession, setRcaRequestSession] = useState(0);

  const effectiveProjectId = projectId ?? null;
  const {
    data: reportResponse,
    isFetching: reportFetching,
    isError: reportError,
    refetch: refetchReport,
    error: reportErrorDetail,
    isRcaQueuePending,
    isProcessing: isRcaProcessing,
    isCompleted: isRcaJobCompleted,
    isUnknown: isRcaJobUnknown,
    isFailed: isRcaFailed,
    errorMessage: rcaErrorMessage,
    isJoiningExistingJob,
    retry: retryRcaJob,
    isRetrying,
    beginFollowingJob,
    staleRegenerationDetected,
    stalePollAsyncJobDetected,
    isAsyncBootstrapping,
    isAwaitingPollPayload,
  } = useGetRcaReport({
    interactionName,
    date: date ?? null,
    enabled: !!interactionName,
    projectId: effectiveProjectId,
    requestSession: rcaRequestSession,
  });

  const regenerateRcaReport = useRegenerateRcaReport();

  const trimmedProjectId =
    effectiveProjectId != null ? String(effectiveProjectId).trim() : "";
  const isProjectIdMissing = trimmedProjectId === "";

  const reportPayload = reportResponse?.data ?? null;
  const structuredReport = extractStructuredReport(reportPayload?.report);
  const hasStructuredV1Content =
    isRcaStructuredReportV1WithContent(structuredReport);
  const reportStatus = reportResponse?.status;
  const isReportHttpOk = reportStatus === RCA_HTTP_STATUS.OK;
  const showReport =
    isReportHttpOk && reportPayload != null && hasStructuredV1Content;

  const isCompletedButInvalid = isRcaJobCompleted && !hasStructuredV1Content;
  const hasNonSuccessResponse =
    reportResponse != null &&
    reportStatus !== undefined &&
    reportStatus !== RCA_HTTP_STATUS.OK;
  const shouldShowError = reportError || hasNonSuccessResponse;

  const isRetryInFlight = reportFetching && shouldShowError;

  const isRegenerateMutating = regenerateRcaReport.isPending;

  /**
   * Show async generation UI when a job is in progress.
   * Modal only shows when generation was explicitly triggered (regenerate or cache miss).
   */
  const showAsyncGenerationUi =
    !isRcaFailed &&
    !showReport &&
    (isAsyncBootstrapping ||
      isAwaitingPollPayload ||
      isRcaQueuePending ||
      isRcaProcessing ||
      isRegenerateMutating ||
      (reportFetching && reportResponse === undefined));

  useEffect(() => {
    return () => {
      if (regenerateDebounceTimerRef.current !== null) {
        window.clearTimeout(regenerateDebounceTimerRef.current);
      }
    };
  }, []);

  const handleRegenerate = useCallback(() => {
    const isInteractionNameInvalid = !interactionName;
    if (isInteractionNameInvalid) return;
    if (regenerateRcaReport.isPending) {
      return;
    }

    if (regenerateDebounceTimerRef.current !== null) {
      window.clearTimeout(regenerateDebounceTimerRef.current);
    }

    regenerateDebounceTimerRef.current = window.setTimeout(() => {
      regenerateRcaReport.mutate(
        {
          interactionName,
          date: date ?? null,
          projectId: trimmedProjectId,
        },
        {
          onSuccess: (res) => {
            if (res.status === RCA_HTTP_STATUS.ACCEPTED) {
              const jobId = getJobIdFromRcaPostResponse(res);
              if (jobId) {
                beginFollowingJob(jobId);
              }
              return;
            }
            if (res.status === RCA_HTTP_STATUS.OK) {
              setRcaRequestSession((s) => s + 1);
            }
          },
        },
      );
      regenerateDebounceTimerRef.current = null;
    }, REGENERATE_DEBOUNCE_MS);
  }, [
    interactionName,
    date,
    trimmedProjectId,
    regenerateRcaReport,
    beginFollowingJob,
  ]);

  if (isProjectIdMissing) {
    return (
      <Box className={classes.container}>
        <Stack align="center" gap="md" className={classes.stateMessage}>
          <ErrorAndEmptyState
            message={ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
            classes={[classes.errorState]}
          />
        </Stack>
      </Box>
    );
  }

  if (isRcaJobUnknown) {
    return (
      <Box className={classes.container}>
        <Alert
          color="red"
          title="Unexpected response"
          variant="light"
          maw={520}
          mx="auto"
          mt="xl"
        >
          <Text size="sm" mb="sm">{ROOT_CAUSE_MESSAGES.RCA_UNKNOWN_JOB_STATUS}</Text>
          <Button
            leftSection={<IconRefresh size={14} />}
            variant="subtle"
            color="red"
            size="xs"
            pl={0}
            onClick={() => {
              void refetchReport();
            }}
          >
            {ROOT_CAUSE_MESSAGES.RCA_STALE_REFRESH}
          </Button>
        </Alert>
      </Box>
    );
  }

  if (isCompletedButInvalid) {
    return (
      <Box className={classes.container}>
        <Alert
          color="red"
          title="Report could not be displayed"
          variant="light"
          maw={520}
          mx="auto"
          mt="xl"
        >
          <Text size="sm" mb="sm">
            {ROOT_CAUSE_MESSAGES.RCA_COMPLETED_INVALID_REPORT}
          </Text>
          <Button
            leftSection={<IconRefresh size={14} />}
            variant="subtle"
            color="red"
            size="xs"
            pl={0}
            loading={isRetrying}
            onClick={() => {
              void retryRcaJob();
            }}
          >
            Retry
          </Button>
        </Alert>
      </Box>
    );
  }

  if (isRcaFailed) {
    return (
      <Box className={classes.container}>
        <Alert
          color="red"
          title="Report generation failed"
          variant="light"
          maw={520}
          mx="auto"
          mt="xl"
        >
          <Text size="sm" mb="sm">
            {rcaErrorMessage?.trim() ? rcaErrorMessage : ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
          </Text>
          <Button
            leftSection={<IconRefresh size={14} />}
            variant="subtle"
            color="red"
            size="xs"
            pl={0}
            loading={isRetrying}
            onClick={() => {
              void retryRcaJob();
            }}
          >
            Retry
          </Button>
        </Alert>
      </Box>
    );
  }

  if (showAsyncGenerationUi) {
    return (
      <Box className={classes.container}>
        <Stack align="center" gap="md" className={classes.stateMessage}>
          {isJoiningExistingJob ? (
            <Alert color="blue" variant="light" maw={520} w="100%">
              {ROOT_CAUSE_MESSAGES.RCA_JOINING_JOB()}
            </Alert>
          ) : null}
          <LoaderWithMessage
            loadingMessage={ROOT_CAUSE_MESSAGES.RCA_WAITING_IN_QUEUE}
          />
        </Stack>
      </Box>
    );
  }

  if (showReport && reportPayload) {
    const cachedAtFormatted = formatRcaReportCachedAt(reportPayload.cachedAt);
    const relativeGeneratedAt = formatRcaReportGeneratedAgo(
      reportPayload.cachedAt,
    );
    return (
      <Stack gap="md" className={classes.container}>
        {staleRegenerationDetected ? (
          <Alert color="yellow" variant="light">
            <Stack gap="sm" align="flex-start">
              <Text size="sm">
                {ROOT_CAUSE_MESSAGES.RCA_STALE_REPORT_BANNER}
              </Text>
              <Button
                size="xs"
                variant="light"
                onClick={() => {
                  void refetchReport();
                }}
              >
                {ROOT_CAUSE_MESSAGES.RCA_STALE_REFRESH}
              </Button>
            </Stack>
          </Alert>
        ) : null}
        {stalePollAsyncJobDetected ? (
          <Alert color="blue" variant="light">
            <Stack gap="sm" align="flex-start">
              <Text size="sm">
                {ROOT_CAUSE_MESSAGES.RCA_STALE_ASYNC_ACTIVITY}
              </Text>
              <Button
                size="xs"
                variant="light"
                onClick={() => {
                  void refetchReport();
                }}
              >
                {ROOT_CAUSE_MESSAGES.RCA_STALE_REFRESH}
              </Button>
            </Stack>
          </Alert>
        ) : null}
        <RcaReportView
          report={reportPayload.report ?? {}}
          cachedAt={cachedAtFormatted}
          relativeGeneratedAt={relativeGeneratedAt}
          onRegenerate={handleRegenerate}
        />
      </Stack>
    );
  }

  const refetch = () => {
    void refetchReport();
  };

  const is404 = reportStatus === RCA_HTTP_STATUS.NOT_FOUND;

  if (shouldShowError) {
    const errorMessage = reportErrorDetail?.message || "";
    const responseErrorMessage = reportResponse?.error?.message || "";
    const isTimeout =
      errorMessage.toLowerCase().includes("timeout") ||
      responseErrorMessage.toLowerCase().includes("timeout");
    const isRequestTimeoutMessage =
      errorMessage === "Request Timeout" ||
      responseErrorMessage === "Request Timeout";
    const shouldShowTimeoutMessage = isTimeout || isRequestTimeoutMessage;

    const displayMessage = is404
      ? ROOT_CAUSE_MESSAGES.FEATURE_OR_NO_DATA
      : shouldShowTimeoutMessage
        ? ROOT_CAUSE_MESSAGES.REQUEST_TIMEOUT
        : ROOT_CAUSE_MESSAGES.GENERIC_ERROR;
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
            loading={isRetryInFlight}
            onClick={() => refetch()}
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
}
