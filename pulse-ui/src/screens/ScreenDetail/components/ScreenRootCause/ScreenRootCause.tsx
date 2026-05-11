import {
  Alert,
  Badge,
  Box,
  Button,
  Card,
  Group,
  Modal,
  Skeleton,
  Stack,
  Table,
  Text,
} from "@mantine/core";
import { IconRefresh, IconSparkles } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { useGetScreenRootCause } from "../../../../hooks/useGetScreenRootCause";
import { useGetScreenRcaNarrative } from "../../../../hooks/useGetScreenRcaNarrative";
import { useRegenerateScreenRcaNarrative } from "../../../../hooks";
import { ROOT_CAUSE_MESSAGES } from "../../../CriticalInteractionDetails/components/RootCause/RootCause.constants";
import interactionRcaClasses from "../../../CriticalInteractionDetails/components/RootCause/RootCause.module.css";
import rcaClasses from "../../../CriticalInteractionDetails/components/RootCause/RcaReportView.module.css";
import { RcaRelatedHeatmapCard } from "../../../CriticalInteractionDetails/components/RootCause/RcaRelatedHeatmapCard";
import { RcaSessionReplayEvidenceCard } from "../../../CriticalInteractionDetails/components/RootCause/RcaSessionReplayEvidenceCard";
import {
  SCREEN_ROOT_CAUSE_MESSAGES,
  SCREEN_RCA_METRIC_LABELS,
} from "./ScreenRootCause.constants";
import {
  buildScreenRcaHeatmapFilters,
  rcaWindowFromAnchorAndAsOf,
} from "./buildScreenRcaHeatmapEvidence";
import classes from "./ScreenRootCause.module.css";

const REGENERATE_DEBOUNCE_MS = 500;
/** Delay before showing the “narrative generating” modal (matches interaction RCA UX). */
const NARRATIVE_NOTICE_MODAL_DELAY_MS = 2000;

export interface ScreenRootCauseProps {
  screenName: string;
  projectId: string | null | undefined;
  /** Same as interaction RCA: anchor day + exclusive end instant. */
  date: string;
  asOfIso: string;
}

/** Same formatting as interaction RCA `RootCause` / `RcaReportView` (“Report as of …”). */
function formatReportAsOf(iso: string | null | undefined): string | null {
  if (iso == null || String(iso).trim() === "") return null;
  const parsed = dayjs(iso);
  return parsed.isValid() ? parsed.format("MMM D, YYYY [at] h:mm A") : null;
}

function formatMetricValue(key: string, value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "number" && Number.isFinite(value)) {
    if (
      key === "click_volume" ||
      key.endsWith("_count") ||
      key.includes("frustration")
    ) {
      return Number.isInteger(value) ? String(value) : value.toFixed(1);
    }
    return value.toLocaleString(undefined, { maximumFractionDigits: 2 });
  }
  return String(value);
}

function formatDelta(value: number): string {
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(1)}%`;
}

const BASELINE_ORDER = [
  "click_volume",
  "tap_count",
  "rage_count",
  "dead_count",
  "bad_frustration",
] as const;

export function ScreenRootCause({
  screenName,
  projectId,
  date,
  asOfIso,
}: ScreenRootCauseProps) {
  const regenerateDebounceTimerRef = useRef<number | null>(null);
  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";
  const isProjectIdMissing = trimmedProjectId === "";

  const { windowStartIso, windowEndIso } = useMemo(
    () => rcaWindowFromAnchorAndAsOf(date, asOfIso),
    [date, asOfIso],
  );

  const {
    data: apiResult,
    isLoading,
    isError,
    error,
    refetch,
  } = useGetScreenRootCause({
    screenName,
    date,
    asOfIso,
    projectId,
    enabled:
      !isProjectIdMissing &&
      Boolean(screenName?.trim()) &&
      /^\d{4}-\d{2}-\d{2}$/.test(String(date)) &&
      String(asOfIso).trim() !== "",
  });

  const payload = apiResult?.data ?? null;
  const errMsg =
    apiResult?.error?.message ??
    (error instanceof Error ? error.message : null);

  const shouldShowError = isError || (apiResult?.error != null && !payload);
  const narrativeEnabled =
    !isProjectIdMissing &&
    trimmedProjectId !== "" &&
    Boolean(screenName?.trim()) &&
    windowStartIso.trim() !== "" &&
    windowEndIso.trim() !== "" &&
    !isLoading &&
    !shouldShowError &&
    payload != null &&
    !payload.noDataAvailable;

  const {
    data: narrativeApi,
    isLoading: narrativeLoading,
    isError: narrativeIsError,
    error: narrativeError,
  } = useGetScreenRcaNarrative({
    screenName,
    anchorDate: date,
    windowStartIso,
    windowEndIso,
    projectId,
    rootCauseData: payload ?? null,
    enabled: narrativeEnabled,
  });

  const regenerateScreenNarrative = useRegenerateScreenRcaNarrative();
  const narrativeBusy = narrativeLoading || regenerateScreenNarrative.isPending;

  const narrative = narrativeApi?.data?.report?.narrative ?? null;
  const executiveSummaryText = narrative?.executive_summary?.trim() ?? "";
  const recommendationLines = (narrative?.recommendations ?? []).filter(
    (line) => String(line).trim() !== "",
  );
  const hasExecutiveSummary = executiveSummaryText !== "";
  const hasRecommendations = recommendationLines.length > 0;
  const narrativeErrMsg =
    narrativeApi?.error?.message ??
    (narrativeError instanceof Error ? narrativeError.message : null);
  const regenerateErrMsg =
    regenerateScreenNarrative.error instanceof Error
      ? regenerateScreenNarrative.error.message
      : null;

  /** Same pattern as interaction `RootCause`: modal while async RCA job + narrative are in flight. */
  const showNarrativeGenerationWait =
    narrativeEnabled && narrativeBusy && !narrativeIsError;

  const handleRegenerateNarrative = useCallback(() => {
    if (!screenName?.trim() || !payload) return;
    if (regenerateDebounceTimerRef.current !== null) {
      window.clearTimeout(regenerateDebounceTimerRef.current);
    }
    regenerateDebounceTimerRef.current = window.setTimeout(() => {
      regenerateScreenNarrative.mutate({
        screenName: String(screenName).trim(),
        anchorDate: date,
        asOfIso,
        windowStartIso,
        windowEndIso,
        projectId: trimmedProjectId,
      });
      regenerateDebounceTimerRef.current = null;
    }, REGENERATE_DEBOUNCE_MS);
  }, [
    screenName,
    date,
    asOfIso,
    windowStartIso,
    windowEndIso,
    trimmedProjectId,
    payload,
    regenerateScreenNarrative,
  ]);

  useEffect(() => {
    return () => {
      if (regenerateDebounceTimerRef.current !== null) {
        window.clearTimeout(regenerateDebounceTimerRef.current);
      }
    };
  }, []);

  const [userDismissedNarrativeNotice, setUserDismissedNarrativeNotice] =
    useState(false);
  const [isNarrativeNoticeDelayElapsed, setIsNarrativeNoticeDelayElapsed] =
    useState(false);

  useEffect(() => {
    if (!showNarrativeGenerationWait) {
      setUserDismissedNarrativeNotice(false);
    }
  }, [showNarrativeGenerationWait]);

  useEffect(() => {
    if (!showNarrativeGenerationWait) {
      setIsNarrativeNoticeDelayElapsed(false);
      return;
    }
    const timerId = window.setTimeout(() => {
      setIsNarrativeNoticeDelayElapsed(true);
    }, NARRATIVE_NOTICE_MODAL_DELAY_MS);
    return () => {
      window.clearTimeout(timerId);
    };
  }, [showNarrativeGenerationWait]);

  const isNarrativeGenerationNoticeModalOpen =
    showNarrativeGenerationWait &&
    !userDismissedNarrativeNotice &&
    isNarrativeNoticeDelayElapsed;

  if (isProjectIdMissing) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Stack
          align="center"
          gap="md"
          className={interactionRcaClasses.stateMessage}
        >
          <ErrorAndEmptyState
            message={ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
            classes={[interactionRcaClasses.errorState]}
          />
        </Stack>
      </Box>
    );
  }

  if (isLoading) {
    return (
      <Box className={interactionRcaClasses.container}>
        <div className={interactionRcaClasses.skeletonWrapper}>
          <Skeleton height={24} width={200} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} />
        </div>
      </Box>
    );
  }

  if (shouldShowError) {
    const messageLower = (errMsg ?? "").toLowerCase();
    const isTimeout =
      messageLower.includes("timeout") || errMsg === "Request Timeout";
    const displayMessage = isTimeout
      ? ROOT_CAUSE_MESSAGES.REQUEST_TIMEOUT
      : (errMsg ?? ROOT_CAUSE_MESSAGES.GENERIC_ERROR);

    return (
      <Box className={interactionRcaClasses.container}>
        <Stack
          align="center"
          gap="md"
          className={interactionRcaClasses.stateMessage}
        >
          <ErrorAndEmptyState
            message={displayMessage}
            classes={[interactionRcaClasses.errorState]}
          />
          <Button
            className={interactionRcaClasses.retryButton}
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

  if (!payload) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Text className={interactionRcaClasses.stateMessage}>
          {SCREEN_ROOT_CAUSE_MESSAGES.NO_DATA_IN_PERIOD}
        </Text>
      </Box>
    );
  }

  if (payload.noDataAvailable) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Alert color="gray" title="No data in selected period">
          {payload.message ?? SCREEN_ROOT_CAUSE_MESSAGES.NO_DATA_IN_PERIOD}
        </Alert>
      </Box>
    );
  }

  const showEverythingGoodBanner = payload.everythingGood === true;

  const baseline = payload.baseline ?? {};
  const segments = payload.segments ?? [];
  /** Prefer narrative cache/generation time (parity with interaction `RcaReportView`); else tabular CH cache. */
  const narrativeCachedAtRaw = narrativeApi?.data?.cachedAt;
  const narrativeCachedAtIso =
    narrativeCachedAtRaw != null && String(narrativeCachedAtRaw).trim() !== ""
      ? String(narrativeCachedAtRaw).trim()
      : null;
  const reportAsOfIso =
    !narrativeIsError &&
    !regenerateScreenNarrative.isError &&
    narrativeCachedAtIso != null
      ? narrativeCachedAtIso
      : (payload.cachedAt ?? null);
  const reportAsOf = formatReportAsOf(reportAsOfIso);

  return (
    <>
      <Modal
        opened={isNarrativeGenerationNoticeModalOpen}
        onClose={() => setUserDismissedNarrativeNotice(true)}
        title="Generating narrative"
        centered
      >
        <Stack gap="md">
          <Text size="sm">{ROOT_CAUSE_MESSAGES.RCA_WAITING_IN_QUEUE}</Text>
          <Button
            variant="light"
            onClick={() => setUserDismissedNarrativeNotice(true)}
          >
            OK
          </Button>
        </Stack>
      </Modal>
      <Box className={interactionRcaClasses.container}>
        <Stack gap="lg">
          {showEverythingGoodBanner ? (
            <Alert color="teal" title="No frustration signal">
              {payload.message ??
                SCREEN_ROOT_CAUSE_MESSAGES.NO_FRUSTRATION_BODY}
            </Alert>
          ) : null}

          <Group
            justify="space-between"
            align="flex-start"
            wrap="wrap"
            gap="sm"
          >
            {reportAsOf != null ? (
              <Text className={rcaClasses.reportCachedAt} size="sm" c="dimmed">
                Report as of {reportAsOf}
              </Text>
            ) : (
              <div />
            )}
            <Group gap="xs" wrap="wrap" justify="flex-end">
              <Button
                variant="light"
                size="xs"
                leftSection={<IconRefresh size={14} />}
                disabled={narrativeBusy}
                onClick={handleRegenerateNarrative}
              >
                {ROOT_CAUSE_MESSAGES.REGENERATE_REPORT}
              </Button>
            </Group>
          </Group>

          {narrativeBusy ? (
            <Stack gap="sm">
              <Skeleton height={100} radius="md" />
              <Skeleton height={120} radius="md" />
            </Stack>
          ) : null}

          {!narrativeBusy &&
          (narrativeIsError || regenerateScreenNarrative.isError) ? (
            <Alert color="orange" title="AI summary unavailable">
              {regenerateErrMsg ??
                narrativeErrMsg ??
                ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
            </Alert>
          ) : null}

          {!narrativeBusy &&
          !narrativeIsError &&
          !regenerateScreenNarrative.isError &&
          hasExecutiveSummary ? (
            <Card
              padding="lg"
              radius="md"
              withBorder
              className={rcaClasses.executiveSummaryCard}
            >
              <div className={rcaClasses.executiveSummaryTitleRow}>
                <IconSparkles size={18} color="var(--mantine-color-violet-6)" />
                <Text fw={700} size="sm" c="violet.7">
                  Executive summary
                </Text>
              </div>
              <Text
                className={rcaClasses.executiveSummaryBody}
                size="sm"
                lh={1.65}
              >
                {executiveSummaryText}
              </Text>
            </Card>
          ) : null}

          {!narrativeBusy &&
          !narrativeIsError &&
          !regenerateScreenNarrative.isError &&
          hasRecommendations ? (
            <Card
              padding="lg"
              radius="md"
              withBorder
              className={rcaClasses.recommendationsCard}
            >
              <Text
                className={rcaClasses.recommendationsTitle}
                fw={700}
                size="sm"
                c="teal.8"
              >
                Recommendations
              </Text>
              <ul className={rcaClasses.recommendationsList}>
                {recommendationLines.map((item, i) => (
                  <li key={`screen-rca-rec-${i}`}>
                    <Text size="sm" lh={1.65}>
                      {item}
                    </Text>
                  </li>
                ))}
              </ul>
            </Card>
          ) : null}

          {segments.length === 0 ? (
            <Text className={interactionRcaClasses.stateMessage}>
              {SCREEN_ROOT_CAUSE_MESSAGES.NO_SEGMENT_BREAKDOWN}
            </Text>
          ) : (
            <Box>
              <div className={rcaClasses.segmentsSectionTitleRow}>
                <Text fw={700} size="md" tt="uppercase" c="gray.7">
                  Top contributing segments
                </Text>
                <Badge size="sm" variant="light" color="gray">
                  {segments.length}
                </Badge>
              </div>
              <Stack gap="md">
                {segments.map((seg, idx) => {
                  const sessionIds = (seg.affected_sessions ?? []).filter(
                    (id) => String(id).trim() !== "",
                  );
                  const evidenceCount =
                    trimmedProjectId !== ""
                      ? sessionIds.length + 1
                      : sessionIds.length;
                  const showEvidenceStrip =
                    trimmedProjectId !== "" && evidenceCount > 0;

                  return (
                    <Card
                      key={`${seg.label}-${idx}`}
                      padding="lg"
                      radius="md"
                      withBorder
                      className={rcaClasses.segmentCard}
                    >
                      <Text fw={600} mb="xs" size="md">
                        {seg.label}
                      </Text>
                      {seg.dimensions &&
                        Object.keys(seg.dimensions).length > 0 && (
                          <Group gap="xs" mb="sm">
                            {Object.entries(seg.dimensions).map(([k, v]) => (
                              <Badge
                                key={k}
                                variant="light"
                                size="sm"
                                className={classes.dimBadge}
                              >
                                {k}: {v}
                              </Badge>
                            ))}
                          </Group>
                        )}
                      <div className={rcaClasses.tableWrap}>
                        <Table.ScrollContainer minWidth={480}>
                          <Table
                            className={rcaClasses.metricsTable}
                            layout="fixed"
                            striped
                            highlightOnHover
                            withTableBorder
                            horizontalSpacing="sm"
                            verticalSpacing="xs"
                          >
                            <colgroup>
                              <col
                                className={rcaClasses.metricsTableColMetric}
                              />
                              <col
                                className={rcaClasses.metricsTableColNumeric}
                              />
                              <col
                                className={rcaClasses.metricsTableColNumeric}
                              />
                              <col
                                className={rcaClasses.metricsTableColDelta}
                              />
                            </colgroup>
                            <Table.Thead>
                              <Table.Tr>
                                <Table.Th
                                  className={rcaClasses.metricsColMetric}
                                >
                                  <span
                                    className={rcaClasses.metricsThLabelMetric}
                                  >
                                    Metric
                                  </span>
                                </Table.Th>
                                <Table.Th
                                  className={rcaClasses.metricsColNumeric}
                                >
                                  <span
                                    className={rcaClasses.metricsThLabelNumeric}
                                  >
                                    Value
                                  </span>
                                </Table.Th>
                                <Table.Th
                                  className={rcaClasses.metricsColNumeric}
                                >
                                  <span
                                    className={rcaClasses.metricsThLabelNumeric}
                                  >
                                    Baseline
                                  </span>
                                </Table.Th>
                                <Table.Th
                                  className={rcaClasses.metricsColNumericNarrow}
                                >
                                  <span
                                    className={rcaClasses.metricsThLabelNumeric}
                                  >
                                    Delta
                                  </span>
                                </Table.Th>
                              </Table.Tr>
                            </Table.Thead>
                            <Table.Tbody>
                              {BASELINE_ORDER.map((metricKey) => {
                                const segmentVal = seg.metrics?.[metricKey];
                                const baseVal = baseline[metricKey];
                                const d = seg.deltas?.[metricKey];
                                const label =
                                  SCREEN_RCA_METRIC_LABELS[metricKey] ??
                                  metricKey;
                                const deltaStr =
                                  d != null && Number.isFinite(d)
                                    ? formatDelta(d)
                                    : "—";
                                const deltaColor =
                                  d == null || !Number.isFinite(d)
                                    ? undefined
                                    : d < 0
                                      ? ("teal.7" as const)
                                      : d > 0
                                        ? ("red.7" as const)
                                        : undefined;
                                return (
                                  <Table.Tr key={metricKey}>
                                    <Table.Td
                                      className={rcaClasses.metricsColMetric}
                                    >
                                      <Text size="sm" w="100%" ta="start">
                                        {label}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td
                                      className={rcaClasses.metricsColNumeric}
                                    >
                                      <Text
                                        size="sm"
                                        w="100%"
                                        ta="end"
                                        fw={600}
                                      >
                                        {formatMetricValue(
                                          metricKey,
                                          segmentVal,
                                        )}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td
                                      className={rcaClasses.metricsColNumeric}
                                    >
                                      <Text
                                        size="sm"
                                        w="100%"
                                        ta="end"
                                        c="dimmed"
                                      >
                                        {formatMetricValue(metricKey, baseVal)}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td
                                      className={
                                        rcaClasses.metricsColNumericNarrow
                                      }
                                    >
                                      <Text
                                        size="sm"
                                        w="100%"
                                        ta="end"
                                        fw={600}
                                        c={deltaColor}
                                      >
                                        {deltaStr}
                                      </Text>
                                    </Table.Td>
                                  </Table.Tr>
                                );
                              })}
                            </Table.Tbody>
                          </Table>
                        </Table.ScrollContainer>
                      </div>
                      {showEvidenceStrip ? (
                        <Box className={rcaClasses.evidenceSection} mt="md">
                          <div className={rcaClasses.evidenceSectionTitleRow}>
                            <Text
                              className={rcaClasses.evidenceTitle}
                              fw={700}
                              size="sm"
                              tt="uppercase"
                            >
                              Evidence
                            </Text>
                            <Badge
                              size="sm"
                              variant="light"
                              color="teal"
                              circle
                              className={rcaClasses.evidenceCountBadge}
                            >
                              {evidenceCount}
                            </Badge>
                          </div>
                          <Box className={rcaClasses.evidenceCardRow}>
                            {sessionIds.map((sessionId, sessionIdx) => (
                              <Box
                                key={sessionId}
                                className={rcaClasses.evidenceCardSlot}
                              >
                                <RcaSessionReplayEvidenceCard
                                  sessionId={sessionId}
                                  segmentTitle={seg.label}
                                  projectId={trimmedProjectId}
                                  evidenceOrdinal={sessionIdx + 1}
                                  evidenceSessionCount={sessionIds.length}
                                />
                              </Box>
                            ))}
                            <Box className={rcaClasses.evidenceCardSlot}>
                              <RcaRelatedHeatmapCard
                                projectId={trimmedProjectId}
                                screenName={screenName}
                                segmentTitle={seg.label}
                                heatmapFilters={buildScreenRcaHeatmapFilters(
                                  seg.dimensions ?? undefined,
                                  windowStartIso,
                                  windowEndIso,
                                )}
                              />
                            </Box>
                          </Box>
                        </Box>
                      ) : null}
                    </Card>
                  );
                })}
              </Stack>
            </Box>
          )}
        </Stack>
      </Box>
    </>
  );
}
