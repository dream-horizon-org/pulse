import {
  ActionIcon,
  Alert,
  Badge,
  Box,
  Button,
  Card,
  Divider,
  Group,
  Stack,
  Table,
  Text,
  Tooltip,
} from "@mantine/core";
import {
  IconCircleCheck,
  IconInfoCircle,
  IconRefresh,
  IconSparkles,
} from "@tabler/icons-react";
import {
  insightRowHasDisplayableNarrative,
  segmentHasDisplayableBody,
  type ErrorAttributionInsightV1,
  type RcaStructuredMetricRowV1,
  type RcaStructuredReportV1,
} from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import type { RcaReportViewProps } from "./RcaReportView.interface";
import { ERROR_ATTRIBUTION_MESSAGES } from "../ErrorAttribution/ErrorAttribution.constants";
import { RcaEmbeddedErrorAttribution } from "./RcaEmbeddedErrorAttribution";
import {
  RCA_METRICS_COLUMN_TOOLTIPS,
  ROOT_CAUSE_MESSAGES,
} from "./RootCause.constants";
import { getMetricValueTone } from "./rcaMetricTone";
import rcaClasses from "./RcaReportView.module.css";
import rootCauseClasses from "./RootCause.module.css";
import { RcaRelatedHeatmapCard } from "./RcaRelatedHeatmapCard";
import { RcaSessionReplayEvidenceCard } from "./RcaSessionReplayEvidenceCard";

/** Max heatmap tiles per segment (evidence strip). */
const HEATMAP_EVIDENCE_MAX = 2;

const RCA_ERROR_ATTRIBUTION_HEADING = `${ERROR_ATTRIBUTION_MESSAGES.SECTION_TITLE} (correlative)`;

function RcaMetricsThWithHint({
  label,
  tooltip,
  labelClassName,
  alignEnd,
}: {
  label: string;
  tooltip: string;
  labelClassName: string;
  alignEnd: boolean;
}) {
  return (
    <div className={labelClassName}>
      <Group
        gap={4}
        wrap="nowrap"
        justify={alignEnd ? "flex-end" : "flex-start"}
        align="center"
      >
        <span>{label}</span>
        <Tooltip
          label={tooltip}
          multiline
          maw={300}
          withArrow
          events={{ hover: true, focus: true, touch: true }}
        >
          <ActionIcon
            variant="subtle"
            color="gray"
            size="xs"
            radius="xl"
            aria-label={`${label}: more information`}
          >
            <IconInfoCircle size={14} />
          </ActionIcon>
        </Tooltip>
      </Group>
    </div>
  );
}

/** Metric sort order for RCA table - volume first, then by severity. */
const METRIC_PRIORITY_ORDER: string[] = [
  "volume",
  "apdex",
  "error_rate",
  "poor_user_pct",
  "crash_rate",
  "anr_rate",
  "duration_p95",
  "frozen_frame_rate",
  "duration_p50",
  "slow_frame_rate",
];

const sortMetricsByPriority = (
  metrics: RcaStructuredMetricRowV1[],
): RcaStructuredMetricRowV1[] => {
  const priorityIndexMap = new Map<string, number>();
  METRIC_PRIORITY_ORDER.forEach((id, index) => {
    priorityIndexMap.set(id, index);
  });

  return [...metrics].sort((a, b) => {
    const aPriority =
      priorityIndexMap.get(a.metric_id) ?? Number.MAX_SAFE_INTEGER;
    const bPriority =
      priorityIndexMap.get(b.metric_id) ?? Number.MAX_SAFE_INTEGER;
    return aPriority - bPriority;
  });
};

const errorAttributionSignalTitle = (
  signal: ErrorAttributionInsightV1["signal"],
) => {
  switch (signal) {
    case "anr":
      return "ANR";
    case "non_fatal":
      return "Non-fatal errors";
    case "api":
      return "API errors";
    default:
      return signal;
  }
};

const StructuredMetricRow = ({ row }: { row: RcaStructuredMetricRowV1 }) => {
  const tone = getMetricValueTone(
    row.metric_id,
    row.value_number,
    row.baseline_number,
    {
      valueDisplay: row.value_display,
      baselineDisplay: row.baseline_display,
      deltaDisplay: row.delta_display,
    },
  );
  const valueDeltaColor =
    tone === "good"
      ? ("teal.7" as const)
      : tone === "bad"
        ? ("red.7" as const)
        : undefined;

  return (
    <Table.Tr>
      <Table.Td className={rcaClasses.metricsColMetric}>
        <Text size="sm" w="100%" ta="start">
          {row.metric_label}
        </Text>
      </Table.Td>
      <Table.Td className={rcaClasses.metricsColNumeric}>
        <Text
          size="sm"
          w="100%"
          ta="end"
          fw={600}
          c={valueDeltaColor}
          className={
            tone === "neutral" ? rcaClasses.metricValueNeutral : undefined
          }
        >
          {row.value_display}
        </Text>
      </Table.Td>
      <Table.Td className={rcaClasses.metricsColNumeric}>
        <Text size="sm" w="100%" ta="end" c="dimmed">
          {row.baseline_display}
        </Text>
      </Table.Td>
      <Table.Td className={rcaClasses.metricsColNumericNarrow}>
        <Text
          size="sm"
          w="100%"
          ta="end"
          fw={600}
          c={valueDeltaColor}
          className={
            tone === "neutral" ? rcaClasses.metricValueNeutral : undefined
          }
        >
          {row.delta_display}
        </Text>
      </Table.Td>
    </Table.Tr>
  );
};

const RcaStructuredReportV1View = ({
  structured,
  cachedAt,
  analysisLookbackDays,
  onRegenerate,
  projectId,
}: {
  structured: RcaStructuredReportV1;
  cachedAt?: string | null;
  analysisLookbackDays?: number | null;
  onRegenerate?: () => void;
  projectId?: string | null;
}) => {
  const isEverythingGood = structured.everything_good === true;
  const isNoDataAvailable = structured.no_data_available === true;
  const executiveSummaryText = structured.executive_summary?.trim() ?? "";
  const hasExecutiveSummary = executiveSummaryText !== "";
  const segments = structured.segments ?? [];
  const hasSegments = segments.length > 0;
  const recommendations = (structured.recommendations ?? []).filter(
    (line) => String(line).trim() !== "",
  );
  const hasRecommendations = recommendations.length > 0;
  const embeddedErrorAttribution =
    structured.error_attribution ?? structured.errorAttribution ?? null;
  const hasEmbeddedErrorAttribution = embeddedErrorAttribution != null;
  const attributionInsights = structured.error_attribution_insights ?? [];
  const hasMeaningfulAttributionInsights = attributionInsights.some(
    insightRowHasDisplayableNarrative,
  );

  const hasRegenerate = typeof onRegenerate === "function";
  const showAsOf = cachedAt != null && cachedAt !== "";
  const showLookback =
    typeof analysisLookbackDays === "number" &&
    analysisLookbackDays > 0 &&
    Number.isFinite(analysisLookbackDays);
  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";
  const hasProjectForHeatmaps = trimmedProjectId !== "";
  const allSegmentsBodyEmpty =
    segments.length > 0 &&
    segments.every(
      (seg) => !segmentHasDisplayableBody(seg, { hasProjectForHeatmaps }),
    );
  const showDrill = hasEmbeddedErrorAttribution && hasProjectForHeatmaps;
  const relatedCount =
    embeddedErrorAttribution?.relatedAttributions?.length ?? 0;
  const hasRelatedAttributionRows = relatedCount > 0;
  const showMeaningfulDrill =
    showDrill && embeddedErrorAttribution != null && relatedCount > 0;
  /** No drill rows ⇒ nothing to list; hide the whole card (avoid insight-only boilerplate). */
  const showUnifiedErrorAttribution =
    hasRelatedAttributionRows &&
    (hasMeaningfulAttributionInsights || showMeaningfulDrill);

  if (isEverythingGood || isNoDataAvailable) {
    const alertColor = isEverythingGood ? "teal" : "gray";
    const alertTitle = isEverythingGood
      ? ROOT_CAUSE_MESSAGES.EVERYTHING_GOOD
      : ROOT_CAUSE_MESSAGES.NO_DATA_AVAILABLE;
    const alertIcon = isEverythingGood ? (
      <IconCircleCheck size={16} />
    ) : undefined;
    return (
      <Box className={rootCauseClasses.container}>
        <Box className={rcaClasses.reportShell}>
          {hasRegenerate ? (
            <Group justify="flex-end" mb="sm">
              <Button
                variant="light"
                size="xs"
                leftSection={<IconRefresh size={14} />}
                onClick={onRegenerate}
              >
                {ROOT_CAUSE_MESSAGES.REGENERATE_REPORT}
              </Button>
            </Group>
          ) : null}
          <Alert
            color={alertColor}
            variant="light"
            icon={alertIcon}
            title={alertTitle}
          >
            {hasExecutiveSummary ? (
              <Text size="sm" lh={1.65}>
                {executiveSummaryText}
              </Text>
            ) : null}
          </Alert>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={rootCauseClasses.container}>
      <Box className={rcaClasses.reportShell}>
        {(showAsOf || showLookback || hasRegenerate) && (
          <Group
            className={rcaClasses.reportHeaderRow}
            justify="space-between"
            align="flex-start"
            wrap="wrap"
            gap="sm"
          >
            <Stack gap={4} align="flex-start">
              {showAsOf ? (
                <Text
                  className={rcaClasses.reportCachedAt}
                  size="sm"
                  c="dimmed"
                >
                  Report as of {cachedAt}
                </Text>
              ) : null}
              {showLookback ? (
                <Text size="sm" c="dimmed">
                  Telemetry lookback: {analysisLookbackDays}{" "}
                  {analysisLookbackDays === 1 ? "day" : "days"}
                </Text>
              ) : null}
              {!showAsOf && !showLookback ? <div /> : null}
            </Stack>
            {hasRegenerate ? (
              <Button
                variant="light"
                size="xs"
                leftSection={<IconRefresh size={14} />}
                onClick={onRegenerate}
              >
                {ROOT_CAUSE_MESSAGES.REGENERATE_REPORT}
              </Button>
            ) : null}
          </Group>
        )}
        <Stack gap="lg">
          {hasExecutiveSummary && (
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
          )}

          {hasSegments && allSegmentsBodyEmpty ? (
            <Text size="sm" c="dimmed" lh={1.65}>
              {ROOT_CAUSE_MESSAGES.NO_DATA}
            </Text>
          ) : null}

          {hasSegments && !allSegmentsBodyEmpty ? (
            <Box>
              <div className={rcaClasses.segmentsSectionTitleRow}>
                <Text fw={700} size="md" tt="uppercase" c="gray.7">
                  Top contributing segments
                </Text>
              </div>
              <Stack gap="md">
                {segments.map((segment, index) => {
                  const rank = segment.rank ?? index + 1;
                  const showSegmentBody = segmentHasDisplayableBody(segment, {
                    hasProjectForHeatmaps,
                  });
                  const impactText = segment.impact?.trim() ?? "";
                  const hasImpact = impactText !== "";
                  const insightsText = segment.insights?.trim() ?? "";
                  const hasInsights = insightsText !== "";
                  const metrics = segment.metrics ?? [];
                  const sessionIds = (segment.affected_sessions ?? []).filter(
                    (id) => String(id).trim() !== "",
                  );
                  const heatmapScreensRaw = (
                    segment.related_heatmaps?.screens ?? []
                  )
                    .map((s) => String(s).trim())
                    .filter((s) => s !== "");
                  const heatmapScreens = heatmapScreensRaw.slice(
                    0,
                    HEATMAP_EVIDENCE_MAX,
                  );
                  const heatmapFilters =
                    segment.related_heatmaps?.heatmap_filters;
                  const heatmapCards =
                    hasProjectForHeatmaps && heatmapScreens.length > 0;
                  const evidenceCount =
                    sessionIds.length +
                    (heatmapCards ? heatmapScreens.length : 0);
                  const showEvidenceStrip = evidenceCount > 0;
                  return (
                    <Card
                      key={`rca-segment-${rank}-${segment.title}-${index}`}
                      withBorder
                      padding="lg"
                      radius="md"
                      className={rcaClasses.segmentCard}
                    >
                      <div className={rcaClasses.segmentHeader}>
                        <div className={rcaClasses.rankBadge}>{rank}</div>
                        <Text fw={600} size="md" style={{ flex: 1 }}>
                          {segment.title}
                        </Text>
                      </div>
                      {!showSegmentBody ? (
                        <Text size="sm" c="dimmed" mt="xs" lh={1.65}>
                          {ROOT_CAUSE_MESSAGES.RCA_SEGMENT_NO_DETAIL}
                        </Text>
                      ) : null}
                      {showSegmentBody && metrics.length > 0 ? (
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
                                    <RcaMetricsThWithHint
                                      label="Metric"
                                      tooltip={
                                        RCA_METRICS_COLUMN_TOOLTIPS.METRIC
                                      }
                                      labelClassName={
                                        rcaClasses.metricsThLabelMetric
                                      }
                                      alignEnd={false}
                                    />
                                  </Table.Th>
                                  <Table.Th
                                    className={rcaClasses.metricsColNumeric}
                                  >
                                    <RcaMetricsThWithHint
                                      label="Value"
                                      tooltip={
                                        RCA_METRICS_COLUMN_TOOLTIPS.VALUE
                                      }
                                      labelClassName={
                                        rcaClasses.metricsThLabelNumeric
                                      }
                                      alignEnd
                                    />
                                  </Table.Th>
                                  <Table.Th
                                    className={rcaClasses.metricsColNumeric}
                                  >
                                    <RcaMetricsThWithHint
                                      label="Baseline"
                                      tooltip={
                                        RCA_METRICS_COLUMN_TOOLTIPS.BASELINE
                                      }
                                      labelClassName={
                                        rcaClasses.metricsThLabelNumeric
                                      }
                                      alignEnd
                                    />
                                  </Table.Th>
                                  <Table.Th
                                    className={
                                      rcaClasses.metricsColNumericNarrow
                                    }
                                  >
                                    <RcaMetricsThWithHint
                                      label="Delta"
                                      tooltip={
                                        RCA_METRICS_COLUMN_TOOLTIPS.DELTA
                                      }
                                      labelClassName={
                                        rcaClasses.metricsThLabelNumeric
                                      }
                                      alignEnd
                                    />
                                  </Table.Th>
                                </Table.Tr>
                              </Table.Thead>
                              <Table.Tbody>
                                {sortMetricsByPriority(metrics).map(
                                  (row, rowIndex) => (
                                    <StructuredMetricRow
                                      key={`${row.metric_id}-${rowIndex}`}
                                      row={row}
                                    />
                                  ),
                                )}
                              </Table.Tbody>
                            </Table>
                          </Table.ScrollContainer>
                        </div>
                      ) : null}
                      {showSegmentBody && hasImpact && (
                        <div className={rcaClasses.impactCallout}>
                          <Text size="xs" fw={600} c="dimmed" mb={6}>
                            Impact
                          </Text>
                          <Text size="sm" lh={1.6}>
                            {impactText}
                          </Text>
                        </div>
                      )}
                      {showSegmentBody && hasInsights && (
                        <div className={rcaClasses.insightsCallout}>
                          <Text size="xs" fw={600} c="dimmed" mb={6}>
                            Insights
                          </Text>
                          <Text size="sm" lh={1.6}>
                            {insightsText}
                          </Text>
                        </div>
                      )}
                      {showSegmentBody && showEvidenceStrip ? (
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
                                  segmentTitle={segment.title}
                                  projectId={trimmedProjectId || null}
                                  evidenceOrdinal={sessionIdx + 1}
                                  evidenceSessionCount={sessionIds.length}
                                />
                              </Box>
                            ))}
                            {heatmapCards
                              ? heatmapScreens.map((screen) => (
                                  <RcaRelatedHeatmapCard
                                    key={`heatmap-${rank}-${screen}`}
                                    projectId={trimmedProjectId}
                                    screenName={screen}
                                    segmentTitle={segment.title}
                                    heatmapFilters={heatmapFilters}
                                  />
                                ))
                              : null}
                          </Box>
                        </Box>
                      ) : null}
                    </Card>
                  );
                })}
              </Stack>
            </Box>
          ) : null}

          {hasRecommendations && (
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
                {recommendations.map((item, i) => (
                  <li key={`rca-rec-${i}`}>
                    <Text size="sm" lh={1.65}>
                      {item}
                    </Text>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {showUnifiedErrorAttribution ? (
            <Card padding="lg" radius="md" withBorder>
              <Group gap="sm" wrap="wrap" align="center" mb="xs">
                <Text fw={700} size="md" tt="uppercase" c="gray.7">
                  {RCA_ERROR_ATTRIBUTION_HEADING}
                </Text>
                {showMeaningfulDrill ? (
                  <Badge size="sm" variant="light" color="gray">
                    {relatedCount}
                  </Badge>
                ) : null}
              </Group>
              <Text size="xs" c="dimmed" mb="lg" lh={1.55}>
                Narrative summaries interpret drill-down groupings; the table
                lists observational associations from telemetry. Neither proves
                root cause.
              </Text>

              {hasMeaningfulAttributionInsights ? (
                <Stack gap="md">
                  {attributionInsights.map((row) => {
                    const summaryText = row.summary?.trim() ?? "";
                    const caveatText = row.caveat?.trim() ?? "";
                    if (!insightRowHasDisplayableNarrative(row)) {
                      return null;
                    }
                    return (
                      <Box key={row.signal}>
                        <Text
                          size="xs"
                          fw={700}
                          tt="uppercase"
                          c="dimmed"
                          mb={6}
                        >
                          {errorAttributionSignalTitle(row.signal)}
                        </Text>
                        {summaryText !== "" ? (
                          <Text size="sm" lh={1.65}>
                            {summaryText}
                          </Text>
                        ) : null}
                        {caveatText !== "" ? (
                          <Text
                            size="xs"
                            c="dimmed"
                            mt={summaryText !== "" ? 6 : 0}
                            lh={1.55}
                          >
                            {caveatText}
                          </Text>
                        ) : null}
                      </Box>
                    );
                  })}
                </Stack>
              ) : null}

              {hasMeaningfulAttributionInsights && showMeaningfulDrill ? (
                <Divider my="lg" label="Drill-down" labelPosition="left" />
              ) : null}

              {showMeaningfulDrill && embeddedErrorAttribution != null ? (
                <RcaEmbeddedErrorAttribution
                  hideSectionTitle
                  data={embeddedErrorAttribution}
                  projectId={trimmedProjectId}
                />
              ) : null}
            </Card>
          ) : null}
        </Stack>
      </Box>
    </Box>
  );
};

export const RcaReportView = ({
  report,
  cachedAt,
  onRegenerate,
  projectId,
}: RcaReportViewProps) => {
  const analysisLookbackDays =
    report.analysisLookbackDays ?? report.report?.analysisLookbackDays ?? null;
  const structured =
    report.structured ?? report.report?.structured ?? undefined;
  const isValidStructured = structured != null && structured.version === 1;
  const executiveSummaryText = structured?.executive_summary?.trim() ?? "";
  const hasSegmentOrRec =
    (structured?.segments?.length ?? 0) > 0 ||
    (structured?.recommendations?.length ?? 0) > 0;
  const drill =
    structured?.error_attribution ?? structured?.errorAttribution ?? null;
  const hasDrillPayload =
    drill != null && (drill.relatedAttributions?.length ?? 0) > 0;
  const hasRenderableContent =
    isValidStructured &&
    (executiveSummaryText !== "" || hasSegmentOrRec || hasDrillPayload);

  if (!hasRenderableContent || structured == null || structured.version !== 1) {
    return (
      <Box className={rootCauseClasses.container}>
        <Text className={rootCauseClasses.stateMessage}>
          {ROOT_CAUSE_MESSAGES.NO_DATA}
        </Text>
      </Box>
    );
  }

  return (
    <RcaStructuredReportV1View
      structured={structured}
      cachedAt={cachedAt}
      analysisLookbackDays={analysisLookbackDays}
      onRegenerate={onRegenerate}
      projectId={projectId}
    />
  );
};
