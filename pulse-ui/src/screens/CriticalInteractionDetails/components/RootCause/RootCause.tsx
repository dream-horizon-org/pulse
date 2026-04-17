import { Box, Button, Modal, Skeleton, Stack, Text } from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useCallback, useEffect, useRef, useState } from "react";
import { useGetRcaReport } from "../../../../hooks/useGetRcaReport/useGetRcaReport";
import { useRegenerateRcaReport } from "../../../../hooks/useRegenerateRcaReport/useRegenerateRcaReport";
import { isRcaStructuredReportV1WithContent } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import {
  ROOT_CAUSE_GENERATION_NOTICE_MODAL_DELAY_MS,
  ROOT_CAUSE_MESSAGES,
} from "./RootCause.constants";
import type { RootCauseProps } from "./RootCause.interface";
import { ErrorAttribution } from "../ErrorAttribution";
import { RcaReportView } from "./RcaReportView";
import classes from "./RootCause.module.css";

dayjs.extend(utc);

const RCA_HTTP_STATUS = {
  OK: 200,
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

export function RootCause({
  interactionName,
  date,
  projectId,
}: RootCauseProps) {
  const [userDismissedGenerationNotice, setUserDismissedGenerationNotice] =
    useState(false);
  const [
    isGenerationNoticeModalDelayElapsed,
    setIsGenerationNoticeModalDelayElapsed,
  ] = useState(false);
  const regenerateDebounceTimerRef = useRef<number | null>(null);

  const effectiveProjectId = projectId ?? null;
  const {
    data: reportResponse,
    isLoading: reportLoading,
    isFetching: reportFetching,
    isError: reportError,
    refetch: refetchReport,
    error: reportErrorDetail,
  } = useGetRcaReport({
    interactionName,
    date: date ?? null,
    enabled: !!interactionName,
    projectId: effectiveProjectId,
  });

  const regenerateRcaReport = useRegenerateRcaReport();

  const trimmedProjectId =
    effectiveProjectId != null ? String(effectiveProjectId).trim() : "";
  const isProjectIdMissing = trimmedProjectId === "";

  /** Drill-only endpoint; show even when RCA report is missing, empty, or errored. */
  const errorAttributionBlock = interactionName ? (
    <ErrorAttribution
      interactionName={interactionName}
      date={date ?? null}
      projectId={effectiveProjectId}
    />
  ) : null;

  const isAwaitingFirstReportResponse =
    reportFetching && reportResponse === undefined;
  const reportPayload = reportResponse?.data ?? null;
  const hasStructuredV1Content = isRcaStructuredReportV1WithContent(
    reportPayload?.report?.structured,
  );
  const reportStatus = reportResponse?.status;
  const isReportHttpOk = reportStatus === RCA_HTTP_STATUS.OK;
  const showReport =
    isReportHttpOk && reportPayload != null && hasStructuredV1Content;

  const hasNonSuccessResponse =
    reportResponse != null &&
    reportStatus !== undefined &&
    reportStatus !== RCA_HTTP_STATUS.OK;
  const shouldShowError = reportError || hasNonSuccessResponse;
  const isRetryInFlight = reportFetching && shouldShowError;
  const isRegenerateReportInFlight = regenerateRcaReport.isPending;
  const showLoadingUi =
    !isProjectIdMissing &&
    (reportLoading ||
      isAwaitingFirstReportResponse ||
      isRetryInFlight ||
      isRegenerateReportInFlight);

  useEffect(() => {
    const loadingFinished = !showLoadingUi;
    if (loadingFinished) {
      setUserDismissedGenerationNotice(false);
    }
  }, [showLoadingUi]);

  useEffect(() => {
    if (!showLoadingUi) {
      setIsGenerationNoticeModalDelayElapsed(false);
      return;
    }
    const timerId = window.setTimeout(() => {
      setIsGenerationNoticeModalDelayElapsed(true);
    }, ROOT_CAUSE_GENERATION_NOTICE_MODAL_DELAY_MS);
    return () => {
      window.clearTimeout(timerId);
    };
  }, [showLoadingUi]);

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

    if (regenerateDebounceTimerRef.current !== null) {
      window.clearTimeout(regenerateDebounceTimerRef.current);
    }

    regenerateDebounceTimerRef.current = window.setTimeout(() => {
      regenerateRcaReport.mutate({
        interactionName,
        date: date ?? null,
        projectId: trimmedProjectId,
      });
      regenerateDebounceTimerRef.current = null;
    }, REGENERATE_DEBOUNCE_MS);
  }, [interactionName, date, trimmedProjectId, regenerateRcaReport]);

  const isGenerationNoticeModalOpen =
    showLoadingUi &&
    !userDismissedGenerationNotice &&
    isGenerationNoticeModalDelayElapsed;

  const handleDismissGenerationNotice = () => {
    setUserDismissedGenerationNotice(true);
  };

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

  if (showLoadingUi) {
    return (
      <>
        <Modal
          opened={isGenerationNoticeModalOpen}
          onClose={handleDismissGenerationNotice}
          title={ROOT_CAUSE_MESSAGES.REPORT_GENERATION_MODAL_TITLE}
          centered
        >
          <Stack gap="md">
            <Text size="sm">
              {ROOT_CAUSE_MESSAGES.REPORT_GENERATION_MODAL_BODY}
            </Text>
            <Button variant="light" onClick={handleDismissGenerationNotice}>
              {ROOT_CAUSE_MESSAGES.REPORT_GENERATION_MODAL_GOT_IT}
            </Button>
          </Stack>
        </Modal>
        <Stack gap="xl">
          <Box className={classes.container}>
            <div className={classes.skeletonWrapper}>
              <Skeleton height={24} width={200} mb="md" />
              <Skeleton height={120} mb="md" />
              <Skeleton height={120} mb="md" />
              <Skeleton height={120} />
            </div>
          </Box>
          {errorAttributionBlock}
        </Stack>
      </>
    );
  }

  if (showReport && reportPayload) {
    const cachedAtFormatted = formatRcaReportCachedAt(reportPayload.cachedAt);
    return (
      <Stack gap="xl">
        <RcaReportView
          report={reportPayload.report ?? {}}
          cachedAt={cachedAtFormatted}
          onRegenerate={handleRegenerate}
          projectId={trimmedProjectId || null}
        />
        {errorAttributionBlock}
      </Stack>
    );
  }

  const refetch = () => {
    refetchReport();
  };

  const is404 = reportStatus === RCA_HTTP_STATUS.NOT_FOUND;
  const isAiUpstreamError =
    reportStatus === RCA_HTTP_STATUS.BAD_GATEWAY ||
    reportStatus === RCA_HTTP_STATUS.SERVICE_UNAVAILABLE;

  if (shouldShowError) {
    const message = is404
      ? ROOT_CAUSE_MESSAGES.FEATURE_OR_NO_DATA
      : isAiUpstreamError
        ? ROOT_CAUSE_MESSAGES.GENERIC_ERROR
        : (reportErrorDetail?.message ??
          reportResponse?.error?.message ??
          ROOT_CAUSE_MESSAGES.GENERIC_ERROR);
    const messageLower = message.toLowerCase();
    const isTimeout =
      messageLower.includes("timeout") || message === "Request Timeout";
    const displayMessage = isTimeout
      ? ROOT_CAUSE_MESSAGES.REQUEST_TIMEOUT
      : message;
    return (
      <Stack gap="xl">
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
              onClick={() => refetch()}
            >
              Retry
            </Button>
          </Stack>
        </Box>
        {errorAttributionBlock}
      </Stack>
    );
  }

  return (
    <Stack gap="xl">
      <Box className={classes.container}>
        <Text className={classes.stateMessage}>
          {ROOT_CAUSE_MESSAGES.NO_DATA}
        </Text>
      </Box>
      {errorAttributionBlock}
    </Stack>
  );
}
