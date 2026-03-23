import { Badge, Box, Card, Stack, Table, Text } from "@mantine/core";
import { IconSparkles } from "@tabler/icons-react";
import type {
  RcaStructuredMetricRowV1,
  RcaStructuredReportV1,
} from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import type { RcaReportViewProps } from "./RcaReportView.interface";
import { getMetricValueTone } from "./rcaMetricTone";
import rcaClasses from "./RcaReportView.module.css";
import rootCauseClasses from "./RootCause.module.css";

const metricToneClassName = (tone: ReturnType<typeof getMetricValueTone>) => {
  if (tone === "good") return rcaClasses.metricValueGood;
  if (tone === "bad") return rcaClasses.metricValueBad;
  return rcaClasses.metricValueNeutral;
};

const StructuredMetricRow = ({ row }: { row: RcaStructuredMetricRowV1 }) => {
  const tone = getMetricValueTone(
    row.metric_id,
    row.value_number,
    row.baseline_number,
  );
  const toneClassName = metricToneClassName(tone);

  return (
    <Table.Tr>
      <Table.Td>
        <Text size="sm">{row.metric_label}</Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm" className={toneClassName} span>
          {row.value_display}
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm" c="dimmed">
          {row.baseline_display}
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm" className={toneClassName} span>
          {row.delta_display}
        </Text>
      </Table.Td>
    </Table.Tr>
  );
};

const RcaStructuredReportV1View = ({
  structured,
  cachedAt,
}: {
  structured: RcaStructuredReportV1;
  cachedAt?: string | null;
}) => {
  const executiveSummaryText = structured.executive_summary?.trim() ?? "";
  const hasExecutiveSummary = executiveSummaryText !== "";
  const segments = structured.segments ?? [];
  const segmentCount = segments.length;
  const hasSegments = segmentCount > 0;
  const recommendations = (structured.recommendations ?? []).filter(
    (line) => String(line).trim() !== "",
  );
  const hasRecommendations = recommendations.length > 0;

  return (
    <Box className={rootCauseClasses.container}>
      <Box className={rcaClasses.reportShell}>
        {cachedAt != null && cachedAt !== "" && (
          <Text className={rcaClasses.reportCachedAt} size="sm" c="dimmed">
            Report as of {cachedAt}
          </Text>
        )}
        <Stack className={rcaClasses.reportStack}>
          {hasExecutiveSummary && (
            <Card
              padding={0}
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

          {hasSegments && (
            <Box>
              <div className={rcaClasses.segmentsSectionTitleRow}>
                <Text fw={700} size="md" tt="uppercase" c="gray.7">
                  Top contributing segments
                </Text>
                <Badge size="sm" variant="light" color="gray">
                  {segmentCount}
                </Badge>
              </div>
              <Stack className={rcaClasses.segmentStack}>
                {segments.map((segment, index) => {
                  const rank = segment.rank ?? index + 1;
                  const impactText = segment.impact?.trim() ?? "";
                  const hasImpact = impactText !== "";
                  const metrics = segment.metrics ?? [];

                  return (
                    <Card
                      key={`rca-segment-${rank}-${segment.title}-${index}`}
                      withBorder
                      padding={0}
                      radius="md"
                      className={rcaClasses.segmentCard}
                    >
                      <div className={rcaClasses.segmentHeader}>
                        <div className={rcaClasses.rankBadge}>{rank}</div>
                        <Text fw={600} size="md" style={{ flex: 1 }}>
                          {segment.title}
                        </Text>
                      </div>
                      {metrics.length > 0 ? (
                        <div className={rcaClasses.tableWrap}>
                          <Table.ScrollContainer minWidth={480}>
                            <Table
                              className={rcaClasses.metricsTable}
                              striped
                              highlightOnHover
                              withTableBorder
                              horizontalSpacing="sm"
                              verticalSpacing={4}
                            >
                              <Table.Thead>
                                <Table.Tr>
                                  <Table.Th>Metric</Table.Th>
                                  <Table.Th>Value</Table.Th>
                                  <Table.Th>Baseline</Table.Th>
                                  <Table.Th>Delta</Table.Th>
                                </Table.Tr>
                              </Table.Thead>
                              <Table.Tbody>
                                {metrics.map((row, rowIndex) => (
                                  <StructuredMetricRow
                                    key={`${row.metric_id}-${rowIndex}`}
                                    row={row}
                                  />
                                ))}
                              </Table.Tbody>
                            </Table>
                          </Table.ScrollContainer>
                        </div>
                      ) : null}
                      {hasImpact && (
                        <div className={rcaClasses.impactCallout}>
                          <Text size="xs" fw={600} c="dimmed" mb={6}>
                            Impact
                          </Text>
                          <Text size="sm" lh={1.6}>
                            {impactText}
                          </Text>
                        </div>
                      )}
                    </Card>
                  );
                })}
              </Stack>
            </Box>
          )}

          {hasRecommendations && (
            <Card
              padding={0}
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
        </Stack>
      </Box>
    </Box>
  );
};

export const RcaReportView = ({ report, cachedAt }: RcaReportViewProps) => {
  const structured = report.structured;
  const isValidStructured = structured != null && structured.version === 1;
  const executiveSummaryText = structured?.executive_summary?.trim() ?? "";
  const hasSegmentOrRec =
    (structured?.segments?.length ?? 0) > 0 ||
    (structured?.recommendations?.length ?? 0) > 0;
  const hasRenderableContent =
    isValidStructured && (executiveSummaryText !== "" || hasSegmentOrRec);

  if (!hasRenderableContent || structured == null || structured.version !== 1) {
    return (
      <Box className={rootCauseClasses.container}>
        <Text className={rootCauseClasses.stateMessage}>
          No report content available.
        </Text>
      </Box>
    );
  }

  return (
    <RcaStructuredReportV1View structured={structured} cachedAt={cachedAt} />
  );
};
