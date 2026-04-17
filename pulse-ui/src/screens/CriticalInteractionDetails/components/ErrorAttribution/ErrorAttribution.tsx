import {
  Badge,
  Box,
  Card,
  Divider,
  Group,
  Skeleton,
  Stack,
  Text,
} from "@mantine/core";
import dayjs from "dayjs";
import { useMemo } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import {
  getErrorAttributionWindowIso,
  useGetErrorAttribution,
} from "../../../../hooks/useGetErrorAttribution";
import type {
  ErrorAttributionSignal,
  RelatedAttributionEntry,
} from "../../../../hooks/useGetErrorAttribution";
import { encodeNetworkId } from "../../../NetworkList/utils/networkIdUtils";
import {
  ERROR_ATTRIBUTION_MESSAGES,
  relatedAttributionsEmptyMessage,
} from "./ErrorAttribution.constants";
import type { ErrorAttributionProps } from "./ErrorAttribution.interface";
import classes from "./ErrorAttribution.module.css";
import rootCauseClasses from "../RootCause/RootCause.module.css";
import rcaClasses from "../RootCause/RcaReportView.module.css";

const ALL_DRILL_SIGNALS: ErrorAttributionSignal[] = [
  "crash",
  "anr",
  "non_fatal",
  "api",
];

function formatCachedAt(iso: string | null | undefined): string | null {
  if (iso == null || String(iso).trim() === "") return null;
  const parsed = dayjs(iso);
  return parsed.isValid() ? parsed.format("MMM D, YYYY [at] h:mm A") : null;
}

function UnifiedRelatedAttributionsList({
  rows,
  projectId,
  linkSuffix,
}: {
  rows: RelatedAttributionEntry[];
  projectId: string;
  linkSuffix: string;
}) {
  return (
    <Card
      withBorder
      padding="md"
      radius="md"
      className={rcaClasses.segmentCard}
    >
      <Stack gap={0}>
        {rows.map((row, idx) => {
          const rank = idx + 1;
          if (row.rowKind === "api") {
            const apiId = encodeNetworkId(
              row.url ?? "",
              row.graphqlOperationName ?? undefined,
              row.graphqlOperationType ?? undefined,
            );
            const to = `/projects/${encodeURIComponent(projectId)}/network-apis/${encodeURIComponent(apiId)}${linkSuffix}`;
            return (
              <Box key={`api-${row.url}-${idx}`}>
                {idx > 0 ? (
                  <Divider size="xs" className={classes.compactDivider} />
                ) : null}
                <div className={classes.compactRow}>
                  <div className={classes.compactRankBadge}>{rank}</div>
                  <Text
                    component={Link}
                    to={to}
                    fw={600}
                    size="sm"
                    className={classes.drillDownLink}
                    style={{ flex: 1, minWidth: 0 }}
                    lineClamp={2}
                    lh={1.35}
                  >
                    {row.url || "(no URL)"}
                  </Text>
                  <Text size="xs" c="dimmed" style={{ flexShrink: 0 }} lh={1.2}>
                    {row.occurrences.toLocaleString()}{" "}
                    {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_SESSIONS}
                  </Text>
                </div>
              </Box>
            );
          }

          const to = `/projects/${encodeURIComponent(projectId)}/app-vitals/${encodeURIComponent(row.groupId ?? "")}${linkSuffix}`;
          const label =
            row.title && row.title.trim() !== ""
              ? row.title
              : row.groupId || "(issue)";
          const typeSuffix =
            row.sourceSignal === "non_fatal" && row.exceptionType
              ? ` (${row.exceptionType})`
              : "";
          return (
            <Box key={`issue-${row.groupId}-${row.exceptionType ?? ""}-${idx}`}>
              {idx > 0 ? (
                <Divider size="xs" className={classes.compactDivider} />
              ) : null}
              <div className={classes.compactRow}>
                <div className={classes.compactRankBadge}>{rank}</div>
                <Text
                  component={Link}
                  to={to}
                  fw={600}
                  size="sm"
                  className={classes.drillDownLink}
                  style={{ flex: 1, minWidth: 0 }}
                  lineClamp={2}
                  lh={1.35}
                >
                  {label}
                  {typeSuffix}
                </Text>
                <Text size="xs" c="dimmed" style={{ flexShrink: 0 }} lh={1.2}>
                  {row.occurrences.toLocaleString()}{" "}
                  {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_SESSIONS}
                </Text>
              </div>
            </Box>
          );
        })}
      </Stack>
    </Card>
  );
}

export function ErrorAttribution({
  interactionName,
  date,
  projectId,
}: ErrorAttributionProps) {
  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";
  const [searchParams] = useSearchParams();
  const linkSuffix = useMemo(() => {
    const qs = searchParams.toString();
    return qs ? `?${qs}` : "";
  }, [searchParams]);

  const { start, end } = useMemo(
    () => getErrorAttributionWindowIso(date ?? null),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset window `end` when RCA scope (project / interaction / date) changes
    [date, interactionName, trimmedProjectId],
  );

  const {
    data: apiResponse,
    isLoading,
    isFetching,
    isError,
  } = useGetErrorAttribution({
    interactionName,
    start,
    end,
    projectId: trimmedProjectId || null,
    drillDownSignals: ALL_DRILL_SIGNALS,
    enabled: trimmedProjectId !== "" && !!interactionName,
  });

  const httpOk = apiResponse?.status === 200 && apiResponse.data != null;
  const body = httpOk ? apiResponse.data : null;

  const showLoading = (isLoading || isFetching) && !httpOk;

  const disclaimerBlock =
    body?.disclaimer != null && String(body.disclaimer).trim() !== "" ? (
      <Text className={classes.disclaimer} size="sm">
        {body.disclaimer}
      </Text>
    ) : null;

  const cachedAtLabel = formatCachedAt(body?.cachedAt);

  if (showLoading) {
    return (
      <Box className={rootCauseClasses.container}>
        <Box className={rcaClasses.reportShell}>
          <div className={rcaClasses.segmentsSectionTitleRow}>
            <Text fw={700} size="md" tt="uppercase" c="gray.7">
              {ERROR_ATTRIBUTION_MESSAGES.SECTION_TITLE}
            </Text>
          </div>
          <Card
            withBorder
            padding="md"
            radius="md"
            className={rcaClasses.segmentCard}
          >
            <Stack gap={0}>
              {[0, 1, 2].map((i) => (
                <Box key={`ea-skel-${i}`}>
                  {i > 0 ? (
                    <Divider size="xs" className={classes.compactDivider} />
                  ) : null}
                  <Group
                    align="center"
                    wrap="nowrap"
                    gap="xs"
                    className={classes.compactRow}
                  >
                    <Skeleton height={22} width={22} radius="sm" />
                    <Skeleton height={16} style={{ flex: 1 }} />
                    <Skeleton height={14} width={88} />
                  </Group>
                </Box>
              ))}
            </Stack>
          </Card>
        </Box>
      </Box>
    );
  }

  if (isError || (apiResponse != null && !httpOk)) {
    return (
      <Box className={rootCauseClasses.container}>
        <Box className={rcaClasses.reportShell}>
          <div className={rcaClasses.segmentsSectionTitleRow}>
            <Text fw={700} size="md" tt="uppercase" c="gray.7">
              {ERROR_ATTRIBUTION_MESSAGES.SECTION_TITLE}
            </Text>
          </div>
          <Card
            withBorder
            padding="md"
            radius="md"
            className={rcaClasses.segmentCard}
          >
            <Stack
              align="center"
              gap="md"
              className={rootCauseClasses.stateMessage}
            >
              <ErrorAndEmptyState
                message={ERROR_ATTRIBUTION_MESSAGES.GENERIC_ERROR}
                classes={[rootCauseClasses.errorState]}
              />
            </Stack>
          </Card>
        </Box>
      </Box>
    );
  }

  if (!body) {
    return null;
  }

  const related = body.relatedAttributions ?? [];

  return (
    <Box className={rootCauseClasses.container}>
      <Box className={rcaClasses.reportShell}>
        <div className={rcaClasses.segmentsSectionTitleRow}>
          <Stack gap={4}>
            <Group gap="sm" wrap="wrap" align="center">
              <Text fw={700} size="md" tt="uppercase" c="gray.7">
                {ERROR_ATTRIBUTION_MESSAGES.SECTION_TITLE}
              </Text>
              {related.length > 0 ? (
                <Badge size="sm" variant="light" color="gray">
                  {related.length}
                </Badge>
              ) : null}
            </Group>
            {cachedAtLabel ? (
              <Text className={rcaClasses.reportCachedAt} size="sm" c="dimmed">
                Cached {cachedAtLabel}
              </Text>
            ) : null}
          </Stack>
        </div>

        {related.length === 0 ? (
          <Card
            withBorder
            padding="md"
            radius="md"
            className={rcaClasses.segmentCard}
          >
            <Text size="sm" c="dimmed">
              {relatedAttributionsEmptyMessage(
                body.minRiskRatioForIssueAttribution,
              )}
            </Text>
          </Card>
        ) : (
          <UnifiedRelatedAttributionsList
            rows={related}
            projectId={trimmedProjectId}
            linkSuffix={linkSuffix}
          />
        )}

        {disclaimerBlock}
      </Box>
    </Box>
  );
}
