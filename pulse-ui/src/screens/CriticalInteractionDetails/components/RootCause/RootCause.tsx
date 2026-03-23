import { Box, Button, Skeleton, Stack, Text } from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useGetRcaReport } from "../../../../hooks/useGetRcaReport/useGetRcaReport";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { ROOT_CAUSE_MESSAGES } from "./RootCause.constants";
import type { RootCauseProps } from "./RootCause.interface";
import { RcaReportView } from "./RcaReportView";
import classes from "./RootCause.module.css";

dayjs.extend(utc);

const RCA_HTTP_STATUS = {
  OK: 200,
  NOT_FOUND: 404,
  BAD_GATEWAY: 502,
  SERVICE_UNAVAILABLE: 503,
} as const;

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
  const effectiveProjectId = projectId ?? null;
  const {
    data: reportResponse,
    isLoading: reportLoading,
    isError: reportError,
    refetch: refetchReport,
    error: reportErrorDetail,
  } = useGetRcaReport({
    interactionName,
    date: date ?? null,
    enabled: !!interactionName,
    projectId: effectiveProjectId,
  });

  const trimmedProjectId =
    effectiveProjectId != null ? String(effectiveProjectId).trim() : "";
  const isProjectIdMissing = trimmedProjectId === "";

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

  const reportPayload = reportResponse?.data ?? null;
  const hasReportStructure = reportPayload?.report != null;
  const reportStatus = reportResponse?.status;
  const isReportHttpOk = reportStatus === RCA_HTTP_STATUS.OK;
  const showReport =
    isReportHttpOk &&
    reportPayload != null &&
    (hasReportStructure ||
      (reportPayload?.rca_insights != null &&
        String(reportPayload.rca_insights).trim() !== ""));

  const isLoading = reportLoading;

  if (isLoading) {
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
    const cachedAtFormatted =
      reportPayload.cached === true
        ? formatRcaReportCachedAt(reportPayload.cachedAt)
        : null;
    const report = reportPayload.report ?? {
      markdown: null,
      charts: [],
      tables: [],
    };
    return (
      <RcaReportView
        report={report}
        rcaInsights={reportPayload.rca_insights}
        cached={reportPayload.cached}
        cachedAt={cachedAtFormatted}
      />
    );
  }

  const refetch = () => {
    refetchReport();
  };

  const is404 = reportStatus === RCA_HTTP_STATUS.NOT_FOUND;
  const isAiUpstreamError =
    reportStatus === RCA_HTTP_STATUS.BAD_GATEWAY ||
    reportStatus === RCA_HTTP_STATUS.SERVICE_UNAVAILABLE;
  const hasNonSuccessResponse =
    reportResponse != null &&
    reportStatus !== undefined &&
    reportStatus !== RCA_HTTP_STATUS.OK;
  const shouldShowError = reportError || hasNonSuccessResponse;

  if (shouldShowError) {
    const message = is404
      ? ROOT_CAUSE_MESSAGES.FEATURE_OR_NO_DATA
      : isAiUpstreamError
        ? ROOT_CAUSE_MESSAGES.AI_SERVICE_UNAVAILABLE
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
