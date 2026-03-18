import { Box, Card, Stack, Table, Text, Title } from "@mantine/core";
import ReactECharts from "echarts-for-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type {
  RcaReportChartBlock,
  RcaReportTableBlock,
} from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import type { RcaReportViewProps } from "./RcaReportView.interface";
import classes from "./RootCause.module.css";

const CHART_LABELS_KEY = "labels";
const CHART_DATASETS_KEY = "datasets";

const buildBarChartOption = (block: RcaReportChartBlock) => {
  const data = block.data ?? {};
  const labels = data[CHART_LABELS_KEY] as string[] | undefined;
  const datasets = data[CHART_DATASETS_KEY] as
    | Array<{ label?: string; data?: number[] }>
    | undefined;
  const seriesData =
    Array.isArray(datasets) && datasets[0]?.data ? datasets[0].data : [];
  const xAxisData = Array.isArray(labels) ? labels : [];

  return {
    title: { text: block.title, left: "center", textStyle: { fontSize: 14 } },
    tooltip: { trigger: "axis" },
    grid: { left: "3%", right: "4%", bottom: "3%", containLabel: true },
    xAxis: { type: "category" as const, data: xAxisData },
    yAxis: { type: "value" as const },
    series: [
      {
        type: "bar" as const,
        data: seriesData,
        name: datasets?.[0]?.label ?? "Value",
      },
    ],
  };
};

const ReportTableBlock = ({ block }: { block: RcaReportTableBlock }) => {
  const columns = block.columns ?? [];
  const rows = block.rows ?? [];
  const firstCol = columns[0] as Record<string, unknown> | undefined;
  const hasKeyLabel =
    columns.length > 0 &&
    firstCol != null &&
    "key" in firstCol &&
    "label" in firstCol;
  const typedCols = hasKeyLabel
    ? (columns as Array<{ key: string; label: string }>)
    : null;
  const headerKeys =
    typedCols != null
      ? typedCols.map((c) => c.key)
      : rows.length > 0
        ? Object.keys(rows[0] as Record<string, unknown>)
        : [];

  if (headerKeys.length === 0) {
    return null;
  }

  const headerLabels =
    typedCols != null ? typedCols.map((c) => c.label) : headerKeys;

  return (
    <Card withBorder padding="md" className={classes.segmentCard}>
      <Title order={6} mb="sm">
        {block.title}
      </Title>
      {block.description != null && block.description !== "" && (
        <Text size="sm" c="dimmed" mb="xs">
          {block.description}
        </Text>
      )}
      <Table striped highlightOnHover withTableBorder>
        <Table.Thead>
          <Table.Tr>
            {headerKeys.map((key, i) => (
              <Table.Th key={key}>{headerLabels[i] ?? key}</Table.Th>
            ))}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((row, ri) => (
            <Table.Tr key={`row-${ri}`}>
              {headerKeys.map((key) => (
                <Table.Td key={key}>
                  {String((row as Record<string, unknown>)[key] ?? "—")}
                </Table.Td>
              ))}
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Card>
  );
};

export const RcaReportView = ({
  report,
  rcaInsights,
  cachedAt,
}: RcaReportViewProps) => {
  const markdownTrimmed = report.markdown?.trim() ?? "";
  const hasMarkdown = markdownTrimmed !== "";
  const charts = report.charts ?? [];
  const tables = report.tables ?? [];
  const hasCharts = charts.length > 0;
  const hasTables = tables.length > 0;
  const insightsTrimmed = rcaInsights?.trim() ?? "";
  const hasInsights = insightsTrimmed !== "";

  const hasContent = hasMarkdown || hasCharts || hasTables || hasInsights;

  if (!hasContent) {
    return (
      <Box className={classes.container}>
        <Text className={classes.stateMessage}>
          No report content available.
        </Text>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      {cachedAt != null && cachedAt !== "" && (
        <Text className={classes.cachedAt} size="sm" c="dimmed">
          Report as of {cachedAt}
        </Text>
      )}
      <Stack gap="lg">
        {hasInsights && (
          <Card withBorder padding="lg" className={classes.insightsCard}>
            <Text className={classes.insightsTitle} component="h2">
              Insights
            </Text>
            <div className={classes.insightsMarkdown}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {insightsTrimmed}
              </ReactMarkdown>
            </div>
          </Card>
        )}
        {hasMarkdown && (
          <Card withBorder padding="md" className={classes.segmentCard}>
            <div className={classes.markdownBlock}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {report.markdown ?? ""}
              </ReactMarkdown>
            </div>
          </Card>
        )}
        {hasCharts &&
          charts.map((chart, index) => (
            <Card
              key={`rca-chart-${chart.title}-${index}`}
              withBorder
              padding="md"
              className={classes.segmentCard}
            >
              <div className={classes.chartWrap}>
                <ReactECharts
                  option={buildBarChartOption(chart)}
                  style={{ height: "100%", width: "100%" }}
                  opts={{ renderer: "svg" }}
                />
              </div>
            </Card>
          ))}
        {hasTables &&
          tables.map((table, index) => (
            <ReportTableBlock
              key={`rca-table-${table.title}-${index}`}
              block={table}
            />
          ))}
      </Stack>
    </Box>
  );
};
