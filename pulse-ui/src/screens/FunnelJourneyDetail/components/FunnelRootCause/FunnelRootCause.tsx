import { Box, Button, Stack, Text } from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import utc from "dayjs/plugin/utc";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { LoaderWithMessage } from "../../../../components/LoaderWithMessage";
import { getJobIdFromRcaPostResponse } from "../../../../helpers/rcaResponseUnwrap";
import { useGetRcaReport } from "../../../../hooks/useGetRcaReport/useGetRcaReport";
import {
  extractStructuredReport,
  isRcaStructuredReportV1WithContent,
} from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import { useRegenerateRcaReport } from "../../../../hooks/useRegenerateRcaReport/useRegenerateRcaReport";
import {
  RCA_TYPE,
  ROOT_CAUSE_MESSAGES,
} from "../../../CriticalInteractionDetails/components/RootCause/RootCause.constants";
import { RcaReportView } from "../../../CriticalInteractionDetails/components/RootCause/RcaReportView";
import rootCauseClasses from "../../../CriticalInteractionDetails/components/RootCause/RootCause.module.css";
import type { FunnelRootCauseProps } from "./FunnelRootCause.interface";

dayjs.extend(utc);
dayjs.extend(relativeTime);

const RCA_HTTP_STATUS = { OK: 200, ACCEPTED: 202 } as const;
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

export function FunnelRootCause({
  funnelId,
  focusStepIndex,
  focusStepName,
  projectId,
  windowStartIso,
  windowEndIso,
  anchorDate,
}: FunnelRootCauseProps) {
  const regenerateDebounceTimerRef = useRef<number | null>(null);
  const [rcaRequestSession, setRcaRequestSession] = useState(0);

  const entityKey = useMemo(
    () => `${funnelId}:${focusStepIndex}`,
    [funnelId, focusStepIndex],
  );

  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";
  const windowReady =
    windowStartIso.trim() !== "" && windowEndIso.trim() !== "";

  const {
    data: reportResponse,
    isFetching: reportFetching,
    isError: reportError,
    isRcaQueuePending,
    isProcessing: isRcaProcessing,
    isCompleted: isRcaJobCompleted,
    isFailed: isRcaFailed,
    errorMessage: rcaErrorMessage,
    isJoiningExistingJob,
    retry: retryRcaJob,
    beginFollowingJob,
    isAsyncBootstrapping,
    isAwaitingPollPayload,
  } = useGetRcaReport({
    entityKey,
    date: anchorDate ?? null,
    rcaType: RCA_TYPE.FUNNEL,
    enabled: trimmedProjectId !== "" && windowReady,
    projectId: trimmedProjectId,
    requestSession: rcaRequestSession,
    windowStartIso,
    windowEndIso,
  });

  const regenerateRcaReport = useRegenerateRcaReport();

  const reportPayload = reportResponse?.data ?? null;
  const structuredReport = extractStructuredReport(reportPayload?.report);
  const hasStructuredV1Content =
    isRcaStructuredReportV1WithContent(structuredReport);
  const showReport =
    reportResponse?.status === RCA_HTTP_STATUS.OK &&
    reportPayload != null &&
    hasStructuredV1Content;

  const showAsyncGenerationUi =
    !isRcaFailed &&
    !showReport &&
    (isAsyncBootstrapping ||
      isAwaitingPollPayload ||
      isRcaQueuePending ||
      isRcaProcessing ||
      regenerateRcaReport.isPending ||
      (reportFetching && reportResponse === undefined));

  const handleRegenerate = useCallback(() => {
    if (regenerateRcaReport.isPending) return;
    if (regenerateDebounceTimerRef.current !== null) {
      window.clearTimeout(regenerateDebounceTimerRef.current);
    }
    regenerateDebounceTimerRef.current = window.setTimeout(() => {
      regenerateRcaReport.mutate(
        {
          entityKey,
          date: anchorDate ?? null,
          projectId: trimmedProjectId,
          rcaType: RCA_TYPE.FUNNEL,
          windowStartIso,
          windowEndIso,
        },
        {
          onSuccess: (res) => {
            if (res.status === RCA_HTTP_STATUS.ACCEPTED) {
              const jobId = getJobIdFromRcaPostResponse(res);
              if (jobId) beginFollowingJob(jobId);
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
    entityKey,
    anchorDate,
    trimmedProjectId,
    windowStartIso,
    windowEndIso,
    regenerateRcaReport,
    beginFollowingJob,
  ]);

  useEffect(() => {
    return () => {
      if (regenerateDebounceTimerRef.current !== null) {
        window.clearTimeout(regenerateDebounceTimerRef.current);
      }
    };
  }, []);

  if (trimmedProjectId === "") {
    return (
      <ErrorAndEmptyState
        message={ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
        classes={[rootCauseClasses.errorState]}
      />
    );
  }

  if (!windowReady) {
    return (
      <Text size="sm" c="dimmed">
        Funnel analysis window is not available for root-cause reporting.
      </Text>
    );
  }

  if (showAsyncGenerationUi) {
    return (
      <LoaderWithMessage
        loadingMessage={
          isJoiningExistingJob
            ? ROOT_CAUSE_MESSAGES.RCA_JOINING_JOB
            : ROOT_CAUSE_MESSAGES.RCA_WAITING_IN_QUEUE
        }
      />
    );
  }

  if (isRcaFailed) {
    return (
      <Stack align="center" gap="md" className={rootCauseClasses.stateMessage}>
        <ErrorAndEmptyState
          message={rcaErrorMessage ?? ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
          classes={[rootCauseClasses.errorState]}
        />
        <Button variant="light" onClick={() => retryRcaJob()}>
          {ROOT_CAUSE_MESSAGES.REGENERATE_REPORT}
        </Button>
      </Stack>
    );
  }

  if (reportError || (isRcaJobCompleted && !hasStructuredV1Content)) {
    return (
      <Stack align="center" gap="md">
        <ErrorAndEmptyState
          message={
            isRcaJobCompleted && !hasStructuredV1Content
              ? ROOT_CAUSE_MESSAGES.RCA_COMPLETED_INVALID_REPORT
              : ROOT_CAUSE_MESSAGES.GENERIC_ERROR
          }
        />
        <Button variant="light" onClick={handleRegenerate}>
          {ROOT_CAUSE_MESSAGES.REGENERATE_REPORT}
        </Button>
      </Stack>
    );
  }

  if (!showReport || structuredReport == null) {
    return (
      <Text size="sm" c="dimmed">
        {ROOT_CAUSE_MESSAGES.NO_DATA}
      </Text>
    );
  }

  const cachedAtFormatted = formatRcaReportCachedAt(reportPayload.cachedAt);
  const relativeGeneratedAt = formatRcaReportGeneratedAgo(
    reportPayload.cachedAt,
  );
  const trimmedStepName =
    focusStepName != null ? String(focusStepName).trim() : "";
  const stepTitle =
    trimmedStepName !== ""
      ? trimmedStepName
      : `Drop-off at step ${focusStepIndex + 1}`;

  return (
    <Box>
      <RcaReportView
        report={reportPayload.report ?? {}}
        cachedAt={cachedAtFormatted}
        relativeGeneratedAt={relativeGeneratedAt}
        onRegenerate={handleRegenerate}
        projectId={trimmedProjectId || null}
        reportContext={{
          badge: `Step ${focusStepIndex + 1}`,
          title: stepTitle,
          subtitle: "AI drop-off report · OTel causes ranked by lift",
          hint: "Select a step in Overview to change focus",
        }}
      />
    </Box>
  );
}
