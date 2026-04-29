import { Badge, Card, Group, Stack, Text } from "@mantine/core";
import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import type { ErrorAttributionResponse } from "../../../../hooks/useGetErrorAttribution/useGetErrorAttribution.interface";
import {
  ERROR_ATTRIBUTION_MESSAGES,
  relatedAttributionsEmptyMessage,
} from "../ErrorAttribution/ErrorAttribution.constants";
import { UnifiedRelatedAttributionsList } from "../ErrorAttribution/ErrorAttribution";
import classes from "../ErrorAttribution/ErrorAttribution.module.css";
import rcaClasses from "./RcaReportView.module.css";

function formatCachedAt(iso: string | null | undefined): string | null {
  if (iso == null || String(iso).trim() === "") return null;
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime())
    ? null
    : parsed.toLocaleString(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "numeric",
        minute: "numeric",
      });
}

type RcaEmbeddedErrorAttributionProps = {
  data: ErrorAttributionResponse;
  projectId: string;
  /** When set, the main section title row is omitted (parent renders one combined RCA heading). */
  hideSectionTitle?: boolean;
};

/** Error distribution merged into cached RCA JSON (`report.structured.errorAttribution`). */
export function RcaEmbeddedErrorAttribution({
  data,
  projectId,
  hideSectionTitle = false,
}: RcaEmbeddedErrorAttributionProps) {
  const [searchParams] = useSearchParams();
  const linkSuffix = useMemo(() => {
    const qs = searchParams.toString();
    return qs ? `?${qs}` : "";
  }, [searchParams]);

  const related = data.relatedAttributions ?? [];
  const cachedAtLabel = formatCachedAt(data.cachedAt ?? null);

  const disclaimerBlock =
    data.disclaimer != null && String(data.disclaimer).trim() !== "" ? (
      <Text className={classes.disclaimer} size="sm">
        {data.disclaimer}
      </Text>
    ) : null;

  return (
    <Stack gap="sm">
      {!hideSectionTitle ? (
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
      ) : cachedAtLabel ? (
        <Text className={rcaClasses.reportCachedAt} size="sm" c="dimmed">
          Cached {cachedAtLabel}
        </Text>
      ) : null}

      {related.length === 0 ? (
        <Card
          withBorder
          padding="md"
          radius="md"
          className={rcaClasses.segmentCard}
        >
          <Text size="sm" c="dimmed">
            {relatedAttributionsEmptyMessage(
              data.minRiskRatioForIssueAttribution,
            )}
          </Text>
        </Card>
      ) : (
        <UnifiedRelatedAttributionsList
          rows={related}
          projectId={projectId}
          linkSuffix={linkSuffix}
        />
      )}

      {disclaimerBlock}
    </Stack>
  );
}
