import {
  Alert,
  Anchor,
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
import { Link } from "react-router-dom";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { RcaSessionReplayEvidenceCard } from "../CriticalInteractionDetails/components/RootCause/RcaSessionReplayEvidenceCard";
import {
  extractStructuredReport,
  useGetRcaReport,
} from "../../hooks/useGetRcaReport";
import type {
  DegradingInteractionV1,
  RcaReportPayload,
  RcaStructuredMetricRowV1,
  RcaStructuredSegmentV1,
  SessionRcaRootCausePayload,
} from "../../hooks/useGetRcaReport";
import {
  RCA_TYPE,
  ROOT_CAUSE_MESSAGES,
} from "../CriticalInteractionDetails/components/RootCause/RootCause.constants";
import interactionRcaClasses from "../CriticalInteractionDetails/components/RootCause/RootCause.module.css";
import rcaClasses from "../CriticalInteractionDetails/components/RootCause/RcaReportView.module.css";

const REGENERATE_DEBOUNCE_MS = 500;
const NARRATIVE_NOTICE_MODAL_DELAY_MS = 2000;
const SESSION_RCA_ENTITY_KEY = "__session__";

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


function getDeltaColor(
  deltaDisplay: string,
  metricId: string,
): "red.7" | "teal.7" | undefined {
  if (!deltaDisplay || deltaDisplay === "—") return undefined;
  const isNeg = deltaDisplay.trimStart().startsWith("-");
  if (metricId === "session_score") return isNeg ? "red.7" : "teal.7";
  return undefined;
}

function getValueColor(
  row: RcaStructuredMetricRowV1,
): "red.7" | "teal.7" | undefined {
  if (row.metric_id === "session_score" && row.value_number != null) {
    if (row.value_number < 0.4) return "red.7";
    if (row.value_number > 0.8) return "teal.7";
  }
  return undefined;
}

function SessionSegmentCard({
  seg,
  projectId,
}: {
  seg: RcaStructuredSegmentV1;
  projectId?: string | null;
}) {
  const evidenceIds = (seg.affected_sessions ?? []).filter(Boolean);
  const degradingInteractions: DegradingInteractionV1[] = (seg.degrading_interactions ?? []).filter(Boolean);
  const insightText = seg.insights?.trim() ?? "";
  const hasInsight = insightText !== "";
  const showEvidence = evidenceIds.length > 0;
  const showDegradingInteractions = degradingInteractions.length > 0;
  const isCritical = seg.impact === "critical";

  return (
    <Card withBorder padding="lg" radius="md" className={rcaClasses.segmentCard}>
      {/* Header: rank + label + impact badge */}
      <div className={rcaClasses.segmentHeader}>
        <div className={rcaClasses.rankBadge}>{seg.rank}</div>
        <Text fw={600} size="md" style={{ flex: 1 }}>
          {seg.title}
        </Text>
        <Badge
          size="sm"
          variant="light"
          color={isCritical ? "red" : "gray"}
        >
          {seg.impact}
        </Badge>
      </div>

      {/* Metrics table */}
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
                  <span className={rcaClasses.metricsThLabelNumeric}>Value</span>
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
              {(seg.metrics ?? []).map((row) => (
                <Table.Tr key={row.metric_id}>
                  <Table.Td className={rcaClasses.metricsColMetric}>
                    <Text size="sm" ta="start">{row.metric_label}</Text>
                  </Table.Td>
                  <Table.Td className={rcaClasses.metricsColNumeric}>
                    <Text size="sm" ta="end" fw={600} c={getValueColor(row)}>
                      {row.value_display}
                    </Text>
                  </Table.Td>
                  <Table.Td className={rcaClasses.metricsColNumeric}>
                    <Text size="sm" ta="end" c="dimmed">
                      {row.baseline_display}
                    </Text>
                  </Table.Td>
                  <Table.Td className={rcaClasses.metricsColNumericNarrow}>
                    <Text
                      size="sm"
                      ta="end"
                      fw={600}
                      c={getDeltaColor(row.delta_display, row.metric_id)}
                    >
                      {row.delta_display}
                    </Text>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      </div>

      {/* AI insight */}
      {hasInsight && (
        <div className={rcaClasses.insightsCallout}>
          <Text size="xs" fw={600} c="dimmed" mb={6}>Insights</Text>
          <Text size="sm" lh={1.6}>{insightText}</Text>
        </div>
      )}

      {/* Evidence */}
      {showEvidence && (
        <Box className={rcaClasses.evidenceSection}>
          <div className={rcaClasses.evidenceSectionTitleRow}>
            <Text
              className={rcaClasses.evidenceTitle}
              fw={700}
              size="sm"
              tt="uppercase"
            >
              Evidence
            </Text>
            <Badge size="sm" variant="light" color="teal" circle className={rcaClasses.evidenceCountBadge}>
              {evidenceIds.length}
            </Badge>
          </div>
          <Box className={rcaClasses.evidenceCardRow}>
            {evidenceIds.map((sid, idx) => (
              <Box key={sid} className={rcaClasses.evidenceCardSlot}>
                <RcaSessionReplayEvidenceCard
                  sessionId={sid}
                  segmentTitle={seg.title}
                  projectId={projectId}
                  evidenceOrdinal={idx + 1}
                  evidenceSessionCount={evidenceIds.length}
                />
              </Box>
            ))}
          </Box>
        </Box>
      )}

      {/* Degrading interactions */}
      {showDegradingInteractions && (
        <Box className={rcaClasses.evidenceSection}>
          <div className={rcaClasses.evidenceSectionTitleRow}>
            <Text
              className={rcaClasses.evidenceTitle}
              fw={700}
              size="sm"
              tt="uppercase"
            >
              Top degrading interactions
            </Text>
            <Badge size="sm" variant="light" color="orange" circle className={rcaClasses.evidenceCountBadge}>
              {degradingInteractions.length}
            </Badge>
          </div>
          <Stack gap={4}>
            {degradingInteractions.map((interaction) => (
              <Group key={interaction.interactionName} justify="space-between" wrap="nowrap" px="xs">
                <Anchor
                  component={Link}
                  to={`/projects/${projectId ?? ""}/interaction-details/${encodeURIComponent(interaction.interactionName)}`}
                  size="sm"
                  style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
                >
                  {interaction.interactionName}
                </Anchor>
                <Text size="sm" c="dimmed" style={{ flexShrink: 0 }}>
                  avg {interaction.avgApdex.toFixed(2)}
                </Text>
              </Group>
            ))}
          </Stack>
        </Box>
      )}
    </Card>
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
    enabled: !isProjectIdMissing && /^\d{4}-\d{2}-\d{2}$/.test(String(date)) && String(asOfIso).trim() !== "",
    projectId,
    rcaType: RCA_TYPE.SESSION,
    requestSession,
  });

  const narrativeBusy = narrativeLoading || isRcaQueuePending || isProcessing;

  // 200 cache-hit has no job → isCompleted stays false, but data is ready
  const hasReport = isCompleted || narrativeData?.status === 200;

  const reportPayload = narrativeData?.data?.report as RcaReportPayload | null | undefined;
  const innerReport = reportPayload?.report ?? reportPayload;

  const structured = extractStructuredReport(innerReport);
  const rcaPayload = (innerReport?.rootCausePayload ?? null) as SessionRcaRootCausePayload | null;

  const executiveSummaryText = structured?.executive_summary?.trim() ?? "";
  const structuredSegments = structured?.segments ?? [];
  const recommendationLines = (structured?.recommendations ?? []).filter(
    (l) => String(l).trim() !== "",
  );
  const hasExecutiveSummary = executiveSummaryText !== "";
  const hasRecommendations = recommendationLines.length > 0;

  const showNarrativeWait = !isProjectIdMissing && narrativeBusy && !narrativeIsError;

  const handleRegenerate = useCallback(() => {
    if (regenerateTimerRef.current !== null) {
      window.clearTimeout(regenerateTimerRef.current);
    }
    regenerateTimerRef.current = window.setTimeout(() => {
      setRequestSession((s) => s + 1);
      regenerateTimerRef.current = null;
    }, REGENERATE_DEBOUNCE_MS);
  }, []);

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

  const cachedAt = narrativeData?.data?.cachedAt ?? rcaPayload?.cachedAt ?? null;
  const reportAsOf = formatReportAsOf(cachedAt != null ? String(cachedAt) : null);

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

  if (hasReport && rcaPayload?.noDataAvailable) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Alert color="gray" title="No data in selected period">
          {rcaPayload.message ?? "No session data available for the selected period."}
        </Alert>
      </Box>
    );
  }

  const showGoodBanner = hasReport && rcaPayload?.everythingGood === true;
  const mode = rcaPayload?.mode;

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
              {rcaPayload?.message ?? "No quality degradation detected in the selected period."}
            </Alert>
          ) : null}

          {staleRegenerationDetected && (
            <Alert color="blue" title={ROOT_CAUSE_MESSAGES.RCA_STALE_REPORT_BANNER} withCloseButton>
              <Button size="xs" variant="light" onClick={() => setRequestSession((s) => s + 1)}>
                {ROOT_CAUSE_MESSAGES.RCA_STALE_REFRESH}
              </Button>
            </Alert>
          )}

          {/* Header row: as-of date + mode + regenerate */}
          <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm">
            {reportAsOf != null ? (
              <Text size="sm" c="dimmed">
                Report as of {reportAsOf}
              </Text>
            ) : <div />}
            <Group gap="xs">
              {mode != null && (
                <Badge size="sm" variant="outline" color="gray" tt="uppercase">
                  {mode}
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


          {/* Loading skeletons */}
          {narrativeBusy ? (
            <Stack gap="sm">
              <Skeleton height={100} radius="md" />
              <Skeleton height={200} radius="md" />
            </Stack>
          ) : null}

          {/* Error states */}
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
          {hasReport && !narrativeBusy && hasExecutiveSummary ? (
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

          {/* Segment cards */}
          {hasReport && !narrativeBusy && structuredSegments.length > 0 ? (
            <Box>
              <div className={rcaClasses.segmentsSectionTitleRow}>
                <Text fw={700} size="md" tt="uppercase" c="gray.7">
                  Top contributing segments
                </Text>
                <Badge size="sm" variant="light" color="gray">{structuredSegments.length}</Badge>
              </div>
              <Stack gap="md">
                {structuredSegments.map((seg) => (
                  <SessionSegmentCard
                    key={`${seg.title}-${seg.rank}`}
                    seg={seg}
                    projectId={projectId}
                  />
                ))}
              </Stack>
            </Box>
          ) : hasReport && !narrativeBusy && structuredSegments.length === 0 ? (
            <Text className={interactionRcaClasses.stateMessage}>
              No segment breakdown available.
            </Text>
          ) : null}

          {/* Recommendations */}
          {hasReport && !narrativeBusy && hasRecommendations ? (
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
        </Stack>
      </Box>
    </>
  );
}
