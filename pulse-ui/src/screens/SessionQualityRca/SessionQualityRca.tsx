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
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { useGetSessionRca } from "../../hooks/useGetSessionRca";
import { useGetRcaReport } from "../../hooks/useGetRcaReport";
import type { SessionRcaNarrativeV1, SessionRcaSegmentInsight } from "../../hooks/useGetRcaReport";
import {
  RCA_TYPE,
  ROOT_CAUSE_MESSAGES,
} from "../CriticalInteractionDetails/components/RootCause/RootCause.constants";
import interactionRcaClasses from "../CriticalInteractionDetails/components/RootCause/RootCause.module.css";
import rcaClasses from "../CriticalInteractionDetails/components/RootCause/RcaReportView.module.css";
import classes from "./SessionQualityRca.module.css";

const REGENERATE_DEBOUNCE_MS = 500;
const NARRATIVE_NOTICE_MODAL_DELAY_MS = 2000;
const SESSION_RCA_ENTITY_KEY = "__session__";

const SESSION_RCA_METRIC_LABELS: Record<string, string> = {
  volume: "Sessions",
  quality_score: "Quality score",
  quality_score_mean: "Quality mean (µ)",
  quality_score_std: "Quality std (σ)",
  z_score: "Z-score",
};

const SEGMENT_METRIC_ORDER = ["volume", "quality_score", "z_score"] as const;

const BASELINE_ORDER = [
  "volume",
  "quality_score",
  "quality_score_mean",
  "quality_score_std",
] as const;

export interface SessionQualityRcaProps {
  projectId: string | null | undefined;
  date: string;
  asOfIso: string;
}

function formatReportAsOf(iso: string | null | undefined): string | null {
  if (iso == null || String(iso).trim() === "") return null;
  const parsed = dayjs(iso);
  return parsed.isValid() ? parsed.format("MMM D, YYYY [at] h:mm A") : null;
}

function formatMetricValue(key: string, value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "number" && Number.isFinite(value)) {
    if (key === "volume") return value.toLocaleString();
    if (key === "z_score") return value.toFixed(2);
    if (key === "quality_score" || key === "quality_score_mean") {
      return (value * 100).toFixed(1) + "%";
    }
    return value.toLocaleString(undefined, { maximumFractionDigits: 3 });
  }
  return String(value);
}

function formatDelta(value: number): string {
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(1)}%`;
}

function ImpactBadge({ impact }: { impact: string }) {
  const isCritical = impact === "critical";
  return (
    <Badge
      size="sm"
      variant="filled"
      color={isCritical ? "red" : "yellow"}
      className={classes.impactBadge}
    >
      {isCritical ? "CRITICAL" : "NORMAL"}
    </Badge>
  );
}

function SegmentInsightRow({ insight }: { insight: SessionRcaSegmentInsight }) {
  return (
    <Box className={classes.insightRow}>
      <Group gap="xs" mb={4}>
        <Text size="sm" fw={600}>{insight.label}</Text>
        <ImpactBadge impact={insight.impact} />
        {insight.z_score != null && (
          <Text size="xs" c="dimmed">z = {insight.z_score.toFixed(2)}</Text>
        )}
        {insight.quality_score != null && (
          <Text size="xs" c="dimmed">
            quality {(insight.quality_score * 100).toFixed(1)}%
          </Text>
        )}
        {insight.volume_pct != null && (
          <Text size="xs" c="dimmed">{insight.volume_pct.toFixed(1)}% of sessions</Text>
        )}
      </Group>
      <Text size="sm" c="dimmed" lh={1.5}>{insight.key_finding}</Text>
    </Box>
  );
}

export function SessionQualityRca({
  projectId,
  date,
  asOfIso,
}: SessionQualityRcaProps) {
  const regenerateTimerRef = useRef<number | null>(null);
  const pid = projectId != null ? String(projectId).trim() : "";
  const isProjectIdMissing = pid === "";

  const {
    data: apiResult,
    isLoading,
    isError,
    error,
    refetch,
  } = useGetSessionRca({
    date,
    asOfIso,
    projectId,
    enabled:
      !isProjectIdMissing &&
      /^\d{4}-\d{2}-\d{2}$/.test(String(date)) &&
      String(asOfIso).trim() !== "",
  });

  const payload = apiResult?.data ?? null;
  const errMsg =
    apiResult?.error?.message ?? (error instanceof Error ? error.message : null);
  const shouldShowError = isError || (apiResult?.error != null && !payload);

  const narrativeEnabled =
    !isProjectIdMissing &&
    !isLoading &&
    !shouldShowError &&
    payload != null &&
    !payload.noDataAvailable;

  const [requestSession, setRequestSession] = useState(0);

  const {
    data: narrativeData,
    isLoading: narrativeLoading,
    isError: narrativeIsError,
    isRcaQueuePending,
    isProcessing,
    isCompleted,
    isFailed,
    jobId,
    errorMessage: narrativeErrorMsg,
    retry: retryNarrative,
    staleRegenerationDetected,
  } = useGetRcaReport({
    entityKey: SESSION_RCA_ENTITY_KEY,
    date,
    enabled: narrativeEnabled,
    projectId,
    rcaType: RCA_TYPE.SESSION,
    requestSession,
  });

  const narrativeBusy = narrativeLoading || isRcaQueuePending || isProcessing;

  // Extract session narrative from job result
  const reportPayload = narrativeData?.data?.report;
  const narrative: SessionRcaNarrativeV1 | null =
    (reportPayload?.narrative ??
      (reportPayload?.report as { narrative?: SessionRcaNarrativeV1 } | null | undefined)
        ?.narrative ??
      null) as SessionRcaNarrativeV1 | null;

  const executiveSummaryText = narrative?.executive_summary?.trim() ?? "";
  const segmentInsights = (narrative?.segment_insights ?? []).filter(
    (si) => si.label && si.key_finding,
  );
  const recommendationLines = (narrative?.recommendations ?? []).filter(
    (l) => String(l).trim() !== "",
  );
  const hasExecutiveSummary = executiveSummaryText !== "";
  const hasInsights = segmentInsights.length > 0;
  const hasRecommendations = recommendationLines.length > 0;

  const showNarrativeWait = narrativeEnabled && narrativeBusy && !narrativeIsError;

  const handleRegenerate = useCallback(() => {
    if (!payload) return;
    if (regenerateTimerRef.current !== null) {
      window.clearTimeout(regenerateTimerRef.current);
    }
    regenerateTimerRef.current = window.setTimeout(() => {
      setRequestSession((s) => s + 1);
      regenerateTimerRef.current = null;
    }, REGENERATE_DEBOUNCE_MS);
  }, [payload]);

  useEffect(() => {
    return () => {
      if (regenerateTimerRef.current !== null) {
        window.clearTimeout(regenerateTimerRef.current);
      }
    };
  }, []);

  const [dismissedNotice, setDismissedNotice] = useState(false);
  const [noticeDelayElapsed, setNoticeDelayElapsed] = useState(false);

  useEffect(() => {
    if (!showNarrativeWait) {
      setDismissedNotice(false);
    }
  }, [showNarrativeWait]);

  useEffect(() => {
    if (!showNarrativeWait) {
      setNoticeDelayElapsed(false);
      return;
    }
    const t = window.setTimeout(() => setNoticeDelayElapsed(true), NARRATIVE_NOTICE_MODAL_DELAY_MS);
    return () => window.clearTimeout(t);
  }, [showNarrativeWait]);

  const noticeModalOpen = showNarrativeWait && !dismissedNotice && noticeDelayElapsed;

  const cachedAt = narrativeData?.data?.cachedAt;
  const reportAsOf = formatReportAsOf(
    cachedAt != null ? String(cachedAt) : (payload?.cachedAt ?? null),
  );

  if (isProjectIdMissing) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Stack align="center" gap="md" className={interactionRcaClasses.stateMessage}>
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
    const isTimeout = (errMsg ?? "").toLowerCase().includes("timeout");
    return (
      <Box className={interactionRcaClasses.container}>
        <Stack align="center" gap="md" className={interactionRcaClasses.stateMessage}>
          <ErrorAndEmptyState
            message={isTimeout ? ROOT_CAUSE_MESSAGES.REQUEST_TIMEOUT : (errMsg ?? ROOT_CAUSE_MESSAGES.GENERIC_ERROR)}
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
        <Text className={interactionRcaClasses.stateMessage}>No data available</Text>
      </Box>
    );
  }

  if (payload.noDataAvailable) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Alert color="gray" title="No data in selected period">
          {payload.message ?? "No session data available for the selected period."}
        </Alert>
      </Box>
    );
  }

  const baseline = payload.baseline ?? {};
  const segments = payload.segments ?? [];
  const showGoodBanner = payload.everythingGood === true;

  return (
    <>
      <Modal
        opened={noticeModalOpen}
        onClose={() => setDismissedNotice(true)}
        title="Generating narrative"
        centered
      >
        <Stack gap="md">
          <Text size="sm">{ROOT_CAUSE_MESSAGES.RCA_WAITING_IN_QUEUE}</Text>
          {jobId != null && (
            <Text size="xs" c="dimmed">Job: {jobId}</Text>
          )}
          <Button variant="light" onClick={() => setDismissedNotice(true)}>OK</Button>
        </Stack>
      </Modal>

      <Box className={interactionRcaClasses.container}>
        <Stack gap="lg">
          {showGoodBanner ? (
            <Alert color="teal" title="Session quality is healthy">
              {payload.message ?? "No quality degradation detected in the selected period."}
            </Alert>
          ) : null}

          {staleRegenerationDetected && (
            <Alert color="blue" title={ROOT_CAUSE_MESSAGES.RCA_STALE_REPORT_BANNER} withCloseButton>
              <Button size="xs" variant="light" onClick={() => setRequestSession((s) => s + 1)}>
                {ROOT_CAUSE_MESSAGES.RCA_STALE_REFRESH}
              </Button>
            </Alert>
          )}

          <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm">
            {reportAsOf != null ? (
              <Text className={rcaClasses.reportCachedAt} size="sm" c="dimmed">
                Report as of {reportAsOf}
              </Text>
            ) : <div />}
            <Group gap="xs">
              {payload.mode != null && (
                <Badge size="sm" variant="outline" color="gray">
                  {payload.mode}
                </Badge>
              )}
              <Button
                variant="light"
                size="xs"
                leftSection={<IconRefresh size={14} />}
                disabled={narrativeBusy}
                onClick={handleRegenerate}
              >
                {ROOT_CAUSE_MESSAGES.REGENERATE_REPORT}
              </Button>
            </Group>
          </Group>

          {/* Baseline */}
          <Card padding="lg" radius="md" withBorder>
            <Text fw={700} size="sm" tt="uppercase" c="gray.7" mb="sm">
              Baseline
            </Text>
            <Group gap="xl" wrap="wrap">
              {BASELINE_ORDER.map((key) => {
                const val = baseline[key];
                if (val == null) return null;
                return (
                  <Box key={key}>
                    <Text size="xs" c="dimmed">{SESSION_RCA_METRIC_LABELS[key] ?? key}</Text>
                    <Text size="sm" fw={600}>{formatMetricValue(key, val)}</Text>
                  </Box>
                );
              })}
            </Group>
          </Card>

          {/* AI narrative loading states */}
          {narrativeBusy ? (
            <Stack gap="sm">
              <Skeleton height={100} radius="md" />
              <Skeleton height={120} radius="md" />
            </Stack>
          ) : null}

          {isFailed && (
            <Alert color="orange" title="AI summary failed">
              {narrativeErrorMsg ?? ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
              <Button size="xs" variant="light" mt="xs" onClick={() => retryNarrative()}>
                Retry
              </Button>
            </Alert>
          )}

          {!narrativeBusy && narrativeIsError && !isFailed ? (
            <Alert color="orange" title="AI summary unavailable">
              {narrativeErrorMsg ?? ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
            </Alert>
          ) : null}

          {/* Executive summary */}
          {isCompleted && !narrativeBusy && hasExecutiveSummary ? (
            <Card padding="lg" radius="md" withBorder className={rcaClasses.executiveSummaryCard}>
              <div className={rcaClasses.executiveSummaryTitleRow}>
                <IconSparkles size={18} color="var(--mantine-color-violet-6)" />
                <Text fw={700} size="sm" c="violet.7">Executive summary</Text>
              </div>
              <Text className={rcaClasses.executiveSummaryBody} size="sm" lh={1.65}>
                {executiveSummaryText}
              </Text>
            </Card>
          ) : null}

          {/* Segment insights from AI */}
          {isCompleted && !narrativeBusy && hasInsights ? (
            <Card padding="lg" radius="md" withBorder>
              <Group gap="xs" mb="sm">
                <Text fw={700} size="sm" tt="uppercase" c="gray.7">Segment insights</Text>
                <Badge size="sm" variant="light" color="gray">{segmentInsights.length}</Badge>
              </Group>
              <Stack gap="sm">
                {segmentInsights.map((si, i) => (
                  <SegmentInsightRow key={`${si.label}-${i}`} insight={si} />
                ))}
              </Stack>
            </Card>
          ) : null}

          {/* Recommendations */}
          {isCompleted && !narrativeBusy && hasRecommendations ? (
            <Card padding="lg" radius="md" withBorder className={rcaClasses.recommendationsCard}>
              <Text className={rcaClasses.recommendationsTitle} fw={700} size="sm" c="teal.8">
                Recommendations
              </Text>
              <ul className={rcaClasses.recommendationsList}>
                {recommendationLines.map((item, i) => (
                  <li key={`rec-${i}`}>
                    <Text size="sm" lh={1.65}>{item}</Text>
                  </li>
                ))}
              </ul>
            </Card>
          ) : null}

          {/* Tabular segments */}
          {segments.length === 0 ? (
            <Text className={interactionRcaClasses.stateMessage}>
              No segment breakdown available.
            </Text>
          ) : (
            <Box>
              <div className={rcaClasses.segmentsSectionTitleRow}>
                <Text fw={700} size="md" tt="uppercase" c="gray.7">
                  Top contributing segments
                </Text>
                <Badge size="sm" variant="light" color="gray">{segments.length}</Badge>
              </div>
              <Stack gap="md">
                {segments.map((seg, idx) => {
                  const impact = seg.metrics?.impact;
                  return (
                    <Card
                      key={`${seg.label}-${idx}`}
                      padding="lg"
                      radius="md"
                      withBorder
                      className={rcaClasses.segmentCard}
                    >
                      <Group gap="sm" mb="xs" align="center">
                        <Text fw={600} size="md">{seg.label}</Text>
                        {typeof impact === "string" && (
                          <ImpactBadge impact={impact} />
                        )}
                      </Group>
                      {seg.dimensions && Object.keys(seg.dimensions).length > 0 && (
                        <Group gap="xs" mb="sm">
                          {Object.entries(seg.dimensions).map(([k, v]) => (
                            <Badge key={k} variant="light" size="sm" className={classes.dimBadge}>
                              {k}: {v}
                            </Badge>
                          ))}
                        </Group>
                      )}
                      <div className={rcaClasses.tableWrap}>
                        <Table.ScrollContainer minWidth={400}>
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
                              <col className={rcaClasses.metricsTableColMetric} />
                              <col className={rcaClasses.metricsTableColNumeric} />
                              <col className={rcaClasses.metricsTableColNumeric} />
                              <col className={rcaClasses.metricsTableColDelta} />
                            </colgroup>
                            <Table.Thead>
                              <Table.Tr>
                                <Table.Th className={rcaClasses.metricsColMetric}>
                                  <span className={rcaClasses.metricsThLabelMetric}>Metric</span>
                                </Table.Th>
                                <Table.Th className={rcaClasses.metricsColNumeric}>
                                  <span className={rcaClasses.metricsThLabelNumeric}>Segment</span>
                                </Table.Th>
                                <Table.Th className={rcaClasses.metricsColNumeric}>
                                  <span className={rcaClasses.metricsThLabelNumeric}>Baseline</span>
                                </Table.Th>
                                <Table.Th className={rcaClasses.metricsColNumericNarrow}>
                                  <span className={rcaClasses.metricsThLabelNumeric}>Delta</span>
                                </Table.Th>
                              </Table.Tr>
                            </Table.Thead>
                            <Table.Tbody>
                              {SEGMENT_METRIC_ORDER.map((metricKey) => {
                                const segVal = seg.metrics?.[metricKey];
                                const baseVal = baseline[metricKey];
                                const d = seg.deltas?.[metricKey];
                                const label = SESSION_RCA_METRIC_LABELS[metricKey] ?? metricKey;
                                const deltaStr =
                                  d != null && Number.isFinite(d) ? formatDelta(d) : "—";
                                const deltaColor =
                                  d == null || !Number.isFinite(d)
                                    ? undefined
                                    : metricKey === "quality_score"
                                      ? d < 0
                                        ? ("red.7" as const)
                                        : ("teal.7" as const)
                                      : d < 0
                                        ? ("teal.7" as const)
                                        : ("red.7" as const);
                                return (
                                  <Table.Tr key={metricKey}>
                                    <Table.Td className={rcaClasses.metricsColMetric}>
                                      <Text size="sm" ta="start">{label}</Text>
                                    </Table.Td>
                                    <Table.Td className={rcaClasses.metricsColNumeric}>
                                      <Text size="sm" ta="end" fw={600}>
                                        {formatMetricValue(metricKey, segVal)}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td className={rcaClasses.metricsColNumeric}>
                                      <Text size="sm" ta="end" c="dimmed">
                                        {formatMetricValue(metricKey, baseVal)}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td className={rcaClasses.metricsColNumericNarrow}>
                                      <Text size="sm" ta="end" fw={600} c={deltaColor}>
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
